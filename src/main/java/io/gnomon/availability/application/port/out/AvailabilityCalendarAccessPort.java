package io.gnomon.availability.application.port.out;

import java.time.ZoneId;
import java.util.UUID;

public interface AvailabilityCalendarAccessPort {

  CalendarContext requireWritableCalendar(UUID actorUserId, String tenantSlug, UUID calendarId);

  record CalendarContext(UUID tenantId, UUID calendarId, ZoneId zoneId) {}
}
