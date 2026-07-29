package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.CalendarResult;
import java.util.UUID;

public interface CalendarUseCase {

  CalendarResult get(UUID actorUserId, String tenantSlug, UUID calendarId);

  CalendarResult update(UpdateCalendarCommand command);

  void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId);

  /** Cross-module contract for authorization and timezone resolution of a writable calendar. */
  WritableCalendar requireWritableCalendar(UUID actorUserId, String tenantSlug, UUID calendarId);
}
