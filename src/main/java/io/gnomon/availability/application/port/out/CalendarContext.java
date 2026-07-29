package io.gnomon.availability.application.port.out;

import java.time.ZoneId;
import java.util.UUID;

public record CalendarContext(UUID tenantId, UUID calendarId, ZoneId zoneId) {}
