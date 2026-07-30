package io.gnomon.shared.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisCacheStoreTest {

  private static final String KEY = "gnomon:catalog:tenant:solar";
  private static final Duration TTL = Duration.ofMinutes(10);

  @Mock private StringRedisTemplate redis;
  @Mock private ValueOperations<String, String> values;

  private RedisCacheStore store;

  @BeforeEach
  void setUp() {
    store = new RedisCacheStore(redis);
  }

  @Test
  void get_whenValueExists_shouldReturnHit() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(KEY)).thenReturn("cached-value");

    assertThat(store.get(KEY)).contains("cached-value");
  }

  @Test
  void get_whenValueDoesNotExist_shouldReturnMiss() {
    when(redis.opsForValue()).thenReturn(values);

    assertThat(store.get(KEY)).isEmpty();
  }

  @Test
  void put_shouldStoreValueWithRequestedTtl() {
    when(redis.opsForValue()).thenReturn(values);

    store.put(KEY, "cached-value", TTL);

    verify(values).set(KEY, "cached-value", TTL);
  }

  @Test
  void increment_whenCreatingVersion_shouldSetTtl() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(KEY)).thenReturn(1L);

    long version = store.increment(KEY, TTL);

    assertThat(version).isOne();
    verify(redis).expire(KEY, TTL);
  }

  @Test
  void increment_whenVersionExists_shouldNotResetTtl() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(KEY)).thenReturn(2L);

    long version = store.increment(KEY, TTL);

    assertThat(version).isEqualTo(2L);
    verify(redis, never()).expire(any(), any(Duration.class));
  }

  @Test
  void operations_whenRedisFails_shouldFailOpen() {
    DataAccessResourceFailureException unavailable =
        new DataAccessResourceFailureException("Redis is unavailable");
    when(redis.opsForValue()).thenThrow(unavailable);
    when(redis.delete(KEY)).thenThrow(unavailable);

    assertThat(store.get(KEY)).isEmpty();
    store.put(KEY, "cached-value", TTL);
    store.evict(KEY);
    assertThat(store.increment(KEY, TTL)).isZero();

    verify(redis).delete(KEY);
    verify(redis, never()).expire(eq(KEY), any(Duration.class));
  }
}
