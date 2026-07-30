package io.gnomon.availability.application.port.in;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Cross-module command boundary for advisory public-availability cache invalidation. */
public interface AvailabilityCacheInvalidationUseCase {

  void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId);

  void invalidateBookingDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, Instant startAt, ZoneId calendarZone);
}
