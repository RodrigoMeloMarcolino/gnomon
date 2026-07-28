package io.gnomon.booking.application.port;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public interface BookingAvailabilityPort {

  boolean isAvailable(
      UUID tenantId,
      UUID calendarId,
      int durationMinutes,
      ZoneId zoneId,
      Instant startAt,
      Instant now);
}
