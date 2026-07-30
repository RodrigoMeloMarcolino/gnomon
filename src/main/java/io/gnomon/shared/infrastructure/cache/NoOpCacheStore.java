package io.gnomon.shared.infrastructure.cache;

import java.time.Duration;
import java.util.Optional;

/** Fallback for disabled or unavailable cache infrastructure. */
final class NoOpCacheStore implements CacheStore {

  @Override
  public Optional<String> get(String key) {
    return Optional.empty();
  }

  @Override
  public void put(String key, String value, Duration ttl) {}

  @Override
  public void evict(String key) {}

  @Override
  public long increment(String key, Duration ttl) {
    return 0;
  }
}
