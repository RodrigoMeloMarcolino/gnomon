package io.gnomon.shared.infrastructure.cache;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
class CacheConfiguration {

  @Bean
  CacheStore cacheStore(
      ObjectProvider<StringRedisTemplate> redisTemplate,
      @Value("${gnomon.cache.enabled:true}") boolean cacheEnabled) {
    if (!cacheEnabled) {
      return new NoOpCacheStore();
    }
    StringRedisTemplate template = redisTemplate.getIfAvailable();
    return template == null ? new NoOpCacheStore() : new RedisCacheStore(template);
  }
}
