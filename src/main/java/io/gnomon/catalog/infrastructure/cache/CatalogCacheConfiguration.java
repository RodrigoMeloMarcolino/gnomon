package io.gnomon.catalog.infrastructure.cache;

import io.gnomon.catalog.application.port.out.PublicCatalogCachePort;
import io.gnomon.shared.infrastructure.cache.CacheStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CatalogCacheProperties.class)
class CatalogCacheConfiguration {

  @Bean
  PublicCatalogCachePort publicCatalogCache(
      CacheStore cacheStore, ObjectMapper objectMapper, CatalogCacheProperties properties) {
    return new PublicCatalogCache(cacheStore, objectMapper, properties.ttl());
  }
}
