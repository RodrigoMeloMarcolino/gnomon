package io.gnomon.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.gnomon.catalog.application.port.in.PublicTenantProfileResult;
import io.gnomon.catalog.application.port.in.result.OfferingResult;
import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import io.gnomon.catalog.infrastructure.cache.PublicCatalogCache;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PublicCatalogCacheTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-29T18:00:00Z");

  @Test
  void profile_whenCached_shouldAvoidSecondDatabaseLookup() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicCatalogCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    PublicTenantProfileResult first =
        cache.profile(TENANT_ID, () -> profile("Barbearia Solar", lookups.incrementAndGet()));
    PublicTenantProfileResult second =
        cache.profile(TENANT_ID, () -> profile("Should not be read", lookups.incrementAndGet()));

    assertThat(first.name()).isEqualTo("Barbearia Solar1");
    assertThat(second).isEqualTo(first);
    assertThat(lookups).hasValue(1);
  }

  @Test
  void offerings_afterInvalidation_shouldUseNewVersionAndReload() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicCatalogCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    List<OfferingResult> first =
        cache.offerings(TENANT_ID, null, () -> offerings(lookups.incrementAndGet()));
    cache.invalidateAfterCommit(TENANT_ID);
    List<OfferingResult> second =
        cache.offerings(TENANT_ID, null, () -> offerings(lookups.incrementAndGet()));

    assertThat(first).extracting(OfferingResult::title).containsExactly("Corte 1");
    assertThat(second).extracting(OfferingResult::title).containsExactly("Corte 2");
    assertThat(lookups).hasValue(2);
  }

  @Test
  void calendars_whenCached_shouldRoundTripThePublicCalendarResult() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicCatalogCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    List<PublicCalendarResult> first =
        cache.calendars(TENANT_ID, () -> calendars(lookups.incrementAndGet()));
    List<PublicCalendarResult> second =
        cache.calendars(TENANT_ID, () -> calendars(lookups.incrementAndGet()));

    assertThat(second).isEqualTo(first);
    assertThat(lookups).hasValue(1);
  }

  private static PublicCatalogCache cache(CacheStore store) {
    return new PublicCatalogCache(store, JsonMapper.builder().build(), Duration.ofMinutes(10));
  }

  private static PublicTenantProfileResult profile(String name, int suffix) {
    return new PublicTenantProfileResult(
        TENANT_ID, name + suffix, "barbearia-solar", "America/Fortaleza", "BRL");
  }

  private static List<OfferingResult> offerings(int suffix) {
    return List.of(
        new OfferingResult(
            OFFERING_ID, TENANT_ID, "Corte " + suffix, null, 30, null, true, NOW, NOW));
  }

  private static List<PublicCalendarResult> calendars(int suffix) {
    return List.of(
        new PublicCalendarResult(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            UUID.fromString("30000000-0000-0000-0000-000000000002"),
            "Profissional " + suffix,
            "Agenda " + suffix,
            "America/Fortaleza"));
  }

  private static final class InMemoryCacheStore implements CacheStore {

    private final Map<String, String> values = new HashMap<>();

    @Override
    public Optional<String> get(String key) {
      return Optional.ofNullable(values.get(key));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
      values.put(key, value);
    }

    @Override
    public void evict(String key) {
      values.remove(key);
    }

    @Override
    public long increment(String key, Duration ttl) {
      long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
      values.put(key, Long.toString(next));
      return next;
    }
  }
}
