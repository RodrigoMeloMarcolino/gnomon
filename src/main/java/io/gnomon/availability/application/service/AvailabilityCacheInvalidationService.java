package io.gnomon.availability.application.service;

import io.gnomon.availability.application.port.in.AvailabilityCacheInvalidationUseCase;
import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityCacheInvalidationService implements AvailabilityCacheInvalidationUseCase {

  private final PublicAvailabilityCachePort cache;

  public AvailabilityCacheInvalidationService(PublicAvailabilityCachePort cache) {
    this.cache = cache;
  }

  @Override
  public void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId) {
    cache.invalidateCalendarAfterCommit(tenantId, calendarId);
  }

  @Override
  public void invalidateBookingDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, Instant startAt, ZoneId calendarZone) {
    cache.invalidateDayAfterCommit(
        tenantId, calendarId, offeringId, startAt.atZone(calendarZone).toLocalDate());
  }
}
