package io.gnomon.availability.infrastructure.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gnomon.cache.availability")
public record AvailabilityCacheProperties(Duration ttl) {

  public AvailabilityCacheProperties {
    ttl = ttl == null ? Duration.ofSeconds(60) : ttl;
  }
}
