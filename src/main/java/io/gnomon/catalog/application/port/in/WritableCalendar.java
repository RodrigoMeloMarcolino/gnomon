package io.gnomon.catalog.application.port.in;

import java.time.ZoneId;
import java.util.UUID;

public record WritableCalendar(UUID tenantId, UUID calendarId, ZoneId zoneId) {}
