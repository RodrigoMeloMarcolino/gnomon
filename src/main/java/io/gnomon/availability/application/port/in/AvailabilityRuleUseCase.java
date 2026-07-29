package io.gnomon.availability.application.port.in;

import io.gnomon.availability.application.port.in.result.AvailabilityRuleResult;
import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleUseCase {

  AvailabilityRuleResult create(CreateAvailabilityRuleCommand command);

  List<AvailabilityRuleResult> list(UUID actorUserId, String tenantSlug, UUID calendarId);

  AvailabilityRuleResult get(UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId);

  AvailabilityRuleResult update(UpdateAvailabilityRuleCommand command);

  void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId);
}
