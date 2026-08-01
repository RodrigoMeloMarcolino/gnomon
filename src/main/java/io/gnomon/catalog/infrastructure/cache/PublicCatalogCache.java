package io.gnomon.catalog.infrastructure.cache;

import io.gnomon.catalog.application.port.in.PublicTenantProfileResult;
import io.gnomon.catalog.application.port.in.result.OfferingResult;
import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import io.gnomon.catalog.application.port.out.PublicCatalogCachePort;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import io.gnomon.shared.logging.StructuredEventLogger;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Cache-aside policy for catalog-owned public reads. */
public final class PublicCatalogCache implements PublicCatalogCachePort {

  private static final String KEY_PREFIX = "gnomon:catalog:tenant:";
  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(PublicCatalogCache.class);

  private final CacheStore store;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final Duration versionTtl;

  public PublicCatalogCache(CacheStore store, ObjectMapper objectMapper, Duration ttl) {
    this.store = store;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
    this.versionTtl = ttl.multipliedBy(2);
  }

  public PublicTenantProfileResult profile(
      UUID tenantId, Supplier<PublicTenantProfileResult> databaseLookup) {
    return load(key(tenantId, "profile"), PublicTenantProfileResult.class, databaseLookup);
  }

  public List<PublicCalendarResult> calendars(
      UUID tenantId, Supplier<List<PublicCalendarResult>> databaseLookup) {
    return load(
        key(tenantId, "calendars"),
        new TypeReference<List<PublicCalendarResult>>() {},
        databaseLookup);
  }

  public List<OfferingResult> offerings(
      UUID tenantId, UUID calendarId, Supplier<List<OfferingResult>> databaseLookup) {
    String suffix = calendarId == null ? "offerings" : "offerings:calendar:" + calendarId;
    return load(
        key(tenantId, suffix), new TypeReference<List<OfferingResult>>() {}, databaseLookup);
  }

  /** Advances the tenant catalog version only after the originating mutation commits. */
  public void invalidateAfterCommit(UUID tenantId) {
    Runnable invalidate =
        () -> {
          store.increment(versionKey(tenantId), versionTtl);
          LOGGER.info(
              "cache.invalidated",
              "catalog cache invalidated",
              java.util.Map.of("cache.name", "catalog", "tenant.id", tenantId));
        };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              invalidate.run();
            }
          });
      return;
    }
    invalidate.run();
  }

  private <T> T load(String key, Class<T> type, Supplier<T> databaseLookup) {
    Optional<T> cached = store.get(key).flatMap(value -> deserialize(value, type));
    if (cached.isPresent()) {
      cacheHit();
      return cached.get();
    }
    cacheMiss();
    T value = databaseLookup.get();
    serialize(value).ifPresent(serialized -> store.put(key, serialized, ttl));
    return value;
  }

  private <T> T load(String key, TypeReference<T> type, Supplier<T> databaseLookup) {
    Optional<T> cached = store.get(key).flatMap(value -> deserialize(value, type));
    if (cached.isPresent()) {
      cacheHit();
      return cached.get();
    }
    cacheMiss();
    T value = databaseLookup.get();
    serialize(value).ifPresent(serialized -> store.put(key, serialized, ttl));
    return value;
  }

  private String key(UUID tenantId, String suffix) {
    String version = store.get(versionKey(tenantId)).orElse("0");
    return KEY_PREFIX + tenantId + ":v:" + version + ":" + suffix;
  }

  private static String versionKey(UUID tenantId) {
    return KEY_PREFIX + tenantId + ":version";
  }

  private <T> Optional<T> deserialize(String value, Class<T> type) {
    try {
      return Optional.of(objectMapper.readValue(value, type));
    } catch (JacksonException exception) {
      return Optional.empty();
    }
  }

  private <T> Optional<T> deserialize(String value, TypeReference<T> type) {
    try {
      return Optional.of(objectMapper.readValue(value, type));
    } catch (JacksonException exception) {
      return Optional.empty();
    }
  }

  private Optional<String> serialize(Object value) {
    try {
      return Optional.of(objectMapper.writeValueAsString(value));
    } catch (JacksonException exception) {
      return Optional.empty();
    }
  }

  private static void cacheHit() {
    LOGGER.info("cache.hit", "catalog cache hit", java.util.Map.of("cache.name", "catalog"));
  }

  private static void cacheMiss() {
    LOGGER.info("cache.miss", "catalog cache miss", java.util.Map.of("cache.name", "catalog"));
  }
}
