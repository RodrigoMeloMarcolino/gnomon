package io.gnomon.availability.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.gnomon.availability.infrastructure.cache.PublicAvailabilityCache;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PublicAvailabilityCacheTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final LocalDate LOCAL_DATE = LocalDate.of(2026, 7, 29);

  @Test
  void availableSlots_whenCached_shouldAvoidSecondDatabaseLookup() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicAvailabilityCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    List<Instant> first =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            OFFERING_ID,
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));
    List<Instant> second =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            OFFERING_ID,
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));

    assertThat(second).isEqualTo(first);
    assertThat(lookups).hasValue(1);
  }

  @Test
  void availableSlots_afterCalendarInvalidation_shouldReloadEveryCachedLocalDay() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicAvailabilityCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    List<Instant> first =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            OFFERING_ID,
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));
    cache.invalidateCalendarAfterCommit(TENANT_ID, CALENDAR_ID);
    List<Instant> second =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            OFFERING_ID,
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));

    assertThat(first).containsExactly(Instant.parse("2026-07-29T12:15:00Z"));
    assertThat(second).containsExactly(Instant.parse("2026-07-29T12:30:00Z"));
    assertThat(lookups).hasValue(2);
  }

  @Test
  void availableSlots_forDifferentOfferings_shouldNotCollide() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicAvailabilityCache cache = cache(store);
    AtomicInteger lookups = new AtomicInteger();

    List<Instant> shortOffering =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            OFFERING_ID,
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));
    List<Instant> longOffering =
        cache.availableSlots(
            TENANT_ID,
            CALENDAR_ID,
            UUID.randomUUID(),
            LOCAL_DATE,
            () -> slots(lookups.incrementAndGet()));

    assertThat(shortOffering).isNotEqualTo(longOffering);
    assertThat(lookups).hasValue(2);
  }

  @Test
  void invalidateDay_afterCommit_shouldNotChangeVersionsBeforeCommit() {
    InMemoryCacheStore store = new InMemoryCacheStore();
    PublicAvailabilityCache cache = cache(store);
    TransactionSynchronizationManager.initSynchronization();
    try {
      cache.invalidateDayAfterCommit(TENANT_ID, CALENDAR_ID, OFFERING_ID, LOCAL_DATE);
      assertThat(store.values).isEmpty();
      TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
      assertThat(store.values).hasSize(2);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static PublicAvailabilityCache cache(CacheStore store) {
    return new PublicAvailabilityCache(store, JsonMapper.builder().build(), Duration.ofSeconds(60));
  }

  private static List<Instant> slots(int lookup) {
    return List.of(Instant.parse("2026-07-29T12:" + (lookup == 1 ? "15" : "30") + ":00Z"));
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
