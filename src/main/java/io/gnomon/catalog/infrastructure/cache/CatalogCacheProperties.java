package io.gnomon.catalog.infrastructure.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gnomon.cache.catalog")
public record CatalogCacheProperties(Duration ttl) {

  public CatalogCacheProperties {
    ttl = ttl == null ? Duration.ofMinutes(10) : ttl;
  }
}
