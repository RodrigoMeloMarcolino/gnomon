package io.gnomon.booking.infrastructure.config;

import io.gnomon.booking.application.port.out.AppointmentFingerprint;
import io.gnomon.booking.application.port.out.PhoneCanonicalizer;
import io.gnomon.booking.domain.service.DefaultSlotGenerator;
import io.gnomon.booking.domain.service.SlotGenerator;
import io.gnomon.booking.infrastructure.fingerprint.DefaultAppointmentFingerprint;
import io.gnomon.booking.infrastructure.phone.DefaultPhoneCanonicalizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class BookingConfiguration {

  @Bean
  SlotGenerator slotGenerator() {
    return new DefaultSlotGenerator();
  }

  @Bean
  PhoneCanonicalizer phoneCanonicalizer(BookingProperties properties) {
    return new DefaultPhoneCanonicalizer(properties.defaultPhoneRegion());
  }

  @Bean
  AppointmentFingerprint appointmentFingerprint() {
    return new DefaultAppointmentFingerprint();
  }
}
