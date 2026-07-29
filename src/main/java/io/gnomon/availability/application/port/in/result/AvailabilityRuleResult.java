package io.gnomon.availability.application.port.in.result;

import io.gnomon.availability.domain.model.AvailabilityRule;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityRuleResult(
    UUID id,
    UUID calendarId,
    int weekday,
    LocalTime startTime,
    LocalTime endTime,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static AvailabilityRuleResult from(AvailabilityRule rule) {
    return new AvailabilityRuleResult(
        rule.id(),
        rule.calendarId(),
        rule.weekday().getValue(),
        rule.startTime(),
        rule.endTime(),
        rule.active(),
        rule.createdAt(),
        rule.updatedAt());
  }
}
