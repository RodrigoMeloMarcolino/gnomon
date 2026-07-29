package io.gnomon.availability.application.port.in;

import java.time.LocalTime;
import java.util.UUID;

public record UpdateAvailabilityRuleCommand(
    UUID actorUserId,
    String tenantSlug,
    UUID calendarId,
    UUID ruleId,
    Integer weekday,
    LocalTime startTime,
    LocalTime endTime,
    Boolean active) {}
