package io.gnomon.booking.infrastructure.integration.availability;

import io.gnomon.availability.application.port.in.AvailabilityCacheInvalidationUseCase;
import io.gnomon.booking.application.port.out.BookingAvailabilityCachePort;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingAvailabilityCacheAdapter implements BookingAvailabilityCachePort {

  private final AvailabilityCacheInvalidationUseCase availability;

  BookingAvailabilityCacheAdapter(AvailabilityCacheInvalidationUseCase availability) {
    this.availability = availability;
  }

  @Override
  public void invalidateBookingDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, Instant startAt, ZoneId calendarZone) {
    availability.invalidateBookingDayAfterCommit(
        tenantId, calendarId, offeringId, startAt, calendarZone);
  }
}
