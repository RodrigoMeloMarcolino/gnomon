package io.gnomon.catalog.application;

import java.util.UUID;

public interface CalendarUseCase {

  CalendarResult get(UUID actorUserId, String tenantSlug, UUID calendarId);

  CalendarResult update(UpdateCalendarCommand command);

  void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId);

  record UpdateCalendarCommand(
      UUID actorUserId,
      String tenantSlug,
      UUID calendarId,
      String name,
      String timezone,
      Boolean active) {}
}
