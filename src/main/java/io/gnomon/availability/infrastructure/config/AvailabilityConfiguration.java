package io.gnomon.availability.infrastructure.config;

import io.gnomon.availability.domain.service.AvailabilityCalculator;
import io.gnomon.availability.domain.service.DefaultAvailabilityCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AvailabilityConfiguration {

  @Bean
  AvailabilityCalculator availabilityCalculator() {
    return new DefaultAvailabilityCalculator();
  }
}
