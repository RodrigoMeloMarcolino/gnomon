package io.gnomon.availability.infrastructure.cache;

import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import io.gnomon.shared.logging.StructuredEventLogger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Cache-aside policy for dynamic availability. Only calculated results, never booking validation,
 * use it.
 */
public final class PublicAvailabilityCache implements PublicAvailabilityCachePort {

  private static final String KEY_PREFIX = "gnomon:availability:tenant:";
  private static final TypeReference<List<Instant>> INSTANT_LIST = new TypeReference<>() {};
  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(PublicAvailabilityCache.class);

  private final CacheStore store;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final Duration versionTtl;

  public PublicAvailabilityCache(CacheStore store, ObjectMapper objectMapper, Duration ttl) {
    this.store = store;
    this.objectMapper = objectMapper;
    this.ttl = ttl;
    this.versionTtl = ttl.multipliedBy(2);
  }

  @Override
  public List<Instant> availableSlots(
      UUID tenantId,
      UUID calendarId,
      UUID offeringId,
      LocalDate calendarLocalDate,
      Supplier<List<Instant>> databaseLookup) {
    String key = key(tenantId, calendarId, offeringId, calendarLocalDate);
    Optional<List<Instant>> cached = store.get(key).flatMap(this::deserialize);
    if (cached.isPresent()) {
      cacheHit();
      return cached.get();
    }
    cacheMiss();
    List<Instant> slots = databaseLookup.get();
    serialize(slots).ifPresent(value -> store.put(key, value, ttl));
    return slots;
  }

  @Override
  public void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId) {
    Runnable invalidate =
        () -> {
          store.increment(calendarVersionKey(tenantId, calendarId), versionTtl);
          cacheInvalidated(tenantId, calendarId);
        };
    afterCommit(invalidate);
  }

  @Override
  public void invalidateDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, LocalDate calendarLocalDate) {
    Runnable invalidate =
        () -> {
          store.evict(key(tenantId, calendarId, offeringId, calendarLocalDate));
          store.increment(calendarVersionKey(tenantId, calendarId), versionTtl);
          store.increment(dayVersionKey(tenantId, calendarId, calendarLocalDate), versionTtl);
          cacheInvalidated(tenantId, calendarId);
        };
    afterCommit(invalidate);
  }

  private String key(UUID tenantId, UUID calendarId, UUID offeringId, LocalDate calendarLocalDate) {
    String calendarVersion = store.get(calendarVersionKey(tenantId, calendarId)).orElse("0");
    String dayVersion =
        store.get(dayVersionKey(tenantId, calendarId, calendarLocalDate)).orElse("0");
    return KEY_PREFIX
        + tenantId
        + ":calendar:"
        + calendarId
        + ":v:"
        + calendarVersion
        + ":day:"
        + calendarLocalDate
        + ":v:"
        + dayVersion
        + ":offering:"
        + offeringId
        + ":slots";
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
      return;
    }
    action.run();
  }

  private static String calendarVersionKey(UUID tenantId, UUID calendarId) {
    return KEY_PREFIX + tenantId + ":calendar:" + calendarId + ":version";
  }

  private static String dayVersionKey(UUID tenantId, UUID calendarId, LocalDate calendarLocalDate) {
    return KEY_PREFIX
        + tenantId
        + ":calendar:"
        + calendarId
        + ":day:"
        + calendarLocalDate
        + ":version";
  }

  private Optional<List<Instant>> deserialize(String value) {
    try {
      return Optional.of(objectMapper.readValue(value, INSTANT_LIST));
    } catch (JacksonException exception) {
      return Optional.empty();
    }
  }

  private Optional<String> serialize(List<Instant> slots) {
    try {
      return Optional.of(objectMapper.writeValueAsString(slots));
    } catch (JacksonException exception) {
      return Optional.empty();
    }
  }

  private static void cacheHit() {
    LOGGER.info(
        "cache.hit", "availability cache hit", java.util.Map.of("cache.name", "availability"));
  }

  private static void cacheMiss() {
    LOGGER.info(
        "cache.miss", "availability cache miss", java.util.Map.of("cache.name", "availability"));
  }

  private static void cacheInvalidated(UUID tenantId, UUID calendarId) {
    LOGGER.info(
        "cache.invalidated",
        "availability cache invalidated",
        java.util.Map.of(
            "cache.name", "availability", "tenant.id", tenantId, "calendar.id", calendarId));
  }
}
