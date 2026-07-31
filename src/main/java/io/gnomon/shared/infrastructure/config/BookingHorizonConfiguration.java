package io.gnomon.shared.infrastructure.config;

import io.gnomon.shared.domain.model.BookingHorizon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BookingHorizonConfiguration {

  @Bean
  BookingHorizon bookingHorizon(BookingHorizonProperties properties) {
    return properties.toDomain();
  }
}
