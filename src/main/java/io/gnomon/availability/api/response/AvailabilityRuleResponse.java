package io.gnomon.availability.api.response;

import io.gnomon.availability.application.port.in.result.AvailabilityRuleResult;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResponse(
    UUID id,
    UUID calendarId,
    int weekday,
    LocalTime startTime,
    LocalTime endTime,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static AvailabilityRuleResponse from(AvailabilityRuleResult result) {
    return new AvailabilityRuleResponse(
        result.id(),
        result.calendarId(),
        result.weekday(),
        result.startTime(),
        result.endTime(),
        result.active(),
        result.createdAt(),
        result.updatedAt());
  }
}
