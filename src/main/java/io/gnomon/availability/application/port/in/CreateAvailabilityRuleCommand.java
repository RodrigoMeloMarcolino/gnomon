package io.gnomon.availability.application.port.in;

import java.time.LocalTime;
import java.util.UUID;

public record CreateAvailabilityRuleCommand(
    UUID actorUserId,
    String tenantSlug,
    UUID calendarId,
    int weekday,
    LocalTime startTime,
    LocalTime endTime) {}
