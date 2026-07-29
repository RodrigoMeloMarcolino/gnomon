package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public record UpdateCalendarCommand(
    UUID actorUserId,
    String tenantSlug,
    UUID calendarId,
    String name,
    String timezone,
    Boolean active) {}
