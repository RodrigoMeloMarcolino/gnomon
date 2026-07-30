package io.gnomon.shared.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

/**
 * Technical cache primitive. Cache misses and backend failures are intentionally indistinguishable.
 */
public interface CacheStore {

  Optional<String> get(String key);

  void put(String key, String value, Duration ttl);

  void evict(String key);

  long increment(String key, Duration ttl);
}
