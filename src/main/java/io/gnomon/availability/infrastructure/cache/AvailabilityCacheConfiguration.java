package io.gnomon.availability.infrastructure.cache;

import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AvailabilityCacheProperties.class)
class AvailabilityCacheConfiguration {

  @Bean
  PublicAvailabilityCachePort publicAvailabilityCache(
      CacheStore cacheStore, ObjectMapper objectMapper, AvailabilityCacheProperties properties) {
    return new PublicAvailabilityCache(cacheStore, objectMapper, properties.ttl());
  }
}
