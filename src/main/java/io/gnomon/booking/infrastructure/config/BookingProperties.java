package io.gnomon.booking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gnomon.booking")
public record BookingProperties(String defaultPhoneRegion) {

  public BookingProperties {
    if (defaultPhoneRegion == null || defaultPhoneRegion.isBlank()) {
      defaultPhoneRegion = "BR";
    }
  }
}
