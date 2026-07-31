package io.gnomon.availability.application;

import static org.mockito.Mockito.verify;

import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import io.gnomon.availability.application.service.AvailabilityCacheInvalidationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityCacheInvalidationServiceTest {

  @Mock private PublicAvailabilityCachePort cache;

  @Test
  void invalidateBookingDay_whenInstantIsNearUtcMidnight_shouldUseCalendarLocalDate() {
    UUID tenantId = UUID.randomUUID();
    UUID calendarId = UUID.randomUUID();
    UUID offeringId = UUID.randomUUID();
    ZoneId zone = ZoneId.of("America/Fortaleza");
    Instant startAt = Instant.parse("2026-07-31T02:30:00Z");

    new AvailabilityCacheInvalidationService(cache)
        .invalidateBookingDayAfterCommit(tenantId, calendarId, offeringId, startAt, zone);

    verify(cache)
        .invalidateDayAfterCommit(tenantId, calendarId, offeringId, LocalDate.of(2026, 7, 30));
  }
}
