package io.gnomon.shared.infrastructure.cache;

import io.gnomon.shared.logging.StructuredEventLogger;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisCacheStore implements CacheStore {

  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(RedisCacheStore.class);

  private final StringRedisTemplate redis;

  RedisCacheStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public Optional<String> get(String key) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(key));
    } catch (DataAccessException exception) {
      unavailable("get", exception);
      return Optional.empty();
    }
  }

  @Override
  public void put(String key, String value, Duration ttl) {
    try {
      redis.opsForValue().set(key, value, ttl);
    } catch (DataAccessException exception) {
      unavailable("put", exception);
    }
  }

  @Override
  public void evict(String key) {
    try {
      redis.delete(key);
    } catch (DataAccessException exception) {
      unavailable("evict", exception);
    }
  }

  @Override
  public long increment(String key, Duration ttl) {
    try {
      Long version = redis.opsForValue().increment(key);
      if (version != null && version == 1L) {
        redis.expire(key, ttl);
      }
      return version == null ? 0 : version;
    } catch (DataAccessException exception) {
      unavailable("increment", exception);
      return 0;
    }
  }

  private static void unavailable(String operation, DataAccessException exception) {
    LOGGER.warn(
        "cache.unavailable",
        "cache backend is unavailable",
        Map.of("cache.name", "redis", "cache.operation", operation));
  }
}
