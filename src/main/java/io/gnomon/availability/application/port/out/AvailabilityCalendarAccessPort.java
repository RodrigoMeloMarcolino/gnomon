package io.gnomon.availability.application.port.out;

import java.util.UUID;

public interface AvailabilityCalendarAccessPort {

  CalendarContext requireWritableCalendar(UUID actorUserId, String tenantSlug, UUID calendarId);
}
