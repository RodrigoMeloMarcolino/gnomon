package io.gnomon.availability.application.port.in;

import io.gnomon.availability.application.port.in.result.AvailabilityRuleResult;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleUseCase {

  AvailabilityRuleResult create(CreateAvailabilityRuleCommand command);

  List<AvailabilityRuleResult> list(UUID actorUserId, String tenantSlug, UUID calendarId);

  AvailabilityRuleResult get(UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId);

  AvailabilityRuleResult update(UpdateAvailabilityRuleCommand command);

  void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId);

  record CreateAvailabilityRuleCommand(
      UUID actorUserId,
      String tenantSlug,
      UUID calendarId,
      int weekday,
      LocalTime startTime,
      LocalTime endTime) {}

  record UpdateAvailabilityRuleCommand(
      UUID actorUserId,
      String tenantSlug,
      UUID calendarId,
      UUID ruleId,
      Integer weekday,
      LocalTime startTime,
      LocalTime endTime,
      Boolean active) {}
}
