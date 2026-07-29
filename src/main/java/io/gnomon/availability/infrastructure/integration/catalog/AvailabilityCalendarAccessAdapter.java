package io.gnomon.availability.infrastructure.integration.catalog;

import io.gnomon.availability.application.port.out.AvailabilityCalendarAccessPort;
import io.gnomon.availability.application.port.out.CalendarContext;
import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.catalog.application.port.in.CalendarUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AvailabilityCalendarAccessAdapter implements AvailabilityCalendarAccessPort {

  private final CalendarUseCase calendars;

  AvailabilityCalendarAccessAdapter(CalendarUseCase calendars) {
    this.calendars = calendars;
  }

  @Override
  public CalendarContext requireWritableCalendar(
      UUID actorUserId, String tenantSlug, UUID calendarId) {
    try {
      var calendar = calendars.requireWritableCalendar(actorUserId, tenantSlug, calendarId);
      return new CalendarContext(calendar.tenantId(), calendar.calendarId(), calendar.zoneId());
    } catch (io.gnomon.catalog.domain.exception.CatalogException exception) {
      throw new AvailabilityException(exception.code(), exception.getMessage());
    }
  }
}
