package io.gnomon.shared.infrastructure.config;

import io.gnomon.shared.domain.model.BookingHorizon;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gnomon.booking")
public record BookingHorizonProperties(int maxAdvanceDays) {

  public BookingHorizonProperties {
    if (maxAdvanceDays == 0) {
      maxAdvanceDays = BookingHorizon.DEFAULT_MAX_ADVANCE_DAYS;
    }
  }

  public BookingHorizon toDomain() {
    return new BookingHorizon(maxAdvanceDays);
  }
}
