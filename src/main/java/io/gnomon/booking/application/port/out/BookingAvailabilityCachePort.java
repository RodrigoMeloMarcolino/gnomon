package io.gnomon.booking.application.port.out;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Advisory cache side effect for a newly committed booking. */
public interface BookingAvailabilityCachePort {

  void invalidateBookingDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, Instant startAt, ZoneId calendarZone);
}
