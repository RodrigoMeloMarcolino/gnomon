package io.gnomon.availability.application.service;

import io.gnomon.availability.application.port.in.AvailabilityRuleUseCase;
import io.gnomon.availability.application.port.in.CreateAvailabilityRuleCommand;
import io.gnomon.availability.application.port.in.UpdateAvailabilityRuleCommand;
import io.gnomon.availability.application.port.in.result.AvailabilityRuleResult;
import io.gnomon.availability.application.port.out.AvailabilityCalendarAccessPort;
import io.gnomon.availability.application.port.out.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.availability.domain.model.AvailabilityRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailabilityRuleService implements AvailabilityRuleUseCase {

  private final AvailabilityRuleRepository rules;
  private final AvailabilityCalendarAccessPort calendarAccess;
  private final Clock clock;
  private final PublicAvailabilityCachePort cache;

  @Autowired
  public AvailabilityRuleService(
      AvailabilityRuleRepository rules,
      AvailabilityCalendarAccessPort calendarAccess,
      PublicAvailabilityCachePort cache) {
    this(rules, calendarAccess, cache, Clock.systemUTC());
  }

  public AvailabilityRuleService(
      AvailabilityRuleRepository rules,
      AvailabilityCalendarAccessPort calendarAccess,
      PublicAvailabilityCachePort cache,
      Clock clock) {
    this.rules = rules;
    this.calendarAccess = calendarAccess;
    this.cache = cache;
    this.clock = clock;
  }

  @Override
  @Transactional
  public AvailabilityRuleResult create(CreateAvailabilityRuleCommand command) {
    var calendar =
        calendarAccess.requireWritableCalendar(
            command.actorUserId(), command.tenantSlug(), command.calendarId());
    AvailabilityRule rule =
        AvailabilityRule.create(
            calendar.tenantId(),
            calendar.calendarId(),
            weekday(command.weekday()),
            command.startTime(),
            command.endTime(),
            clock.instant());
    AvailabilityRuleResult result = AvailabilityRuleResult.from(rules.save(rule));
    cache.invalidateCalendarAfterCommit(calendar.tenantId(), calendar.calendarId());
    return result;
  }

  @Override
  public List<AvailabilityRuleResult> list(UUID actorUserId, String tenantSlug, UUID calendarId) {
    var calendar = calendarAccess.requireWritableCalendar(actorUserId, tenantSlug, calendarId);
    return rules.findByTenantIdAndCalendarId(calendar.tenantId(), calendarId).stream()
        .map(AvailabilityRuleResult::from)
        .toList();
  }

  @Override
  public AvailabilityRuleResult get(
      UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId) {
    var calendar = calendarAccess.requireWritableCalendar(actorUserId, tenantSlug, calendarId);
    return AvailabilityRuleResult.from(requireRule(calendar.tenantId(), calendarId, ruleId));
  }

  @Override
  @Transactional
  public AvailabilityRuleResult update(UpdateAvailabilityRuleCommand command) {
    var calendar =
        calendarAccess.requireWritableCalendar(
            command.actorUserId(), command.tenantSlug(), command.calendarId());
    AvailabilityRule rule =
        requireRule(calendar.tenantId(), command.calendarId(), command.ruleId());
    rule.update(
        command.weekday() == null ? null : weekday(command.weekday()),
        command.startTime(),
        command.endTime(),
        command.active(),
        clock.instant());
    AvailabilityRuleResult result = AvailabilityRuleResult.from(rules.save(rule));
    cache.invalidateCalendarAfterCommit(calendar.tenantId(), calendar.calendarId());
    return result;
  }

  @Override
  @Transactional
  public void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId, UUID ruleId) {
    var calendar = calendarAccess.requireWritableCalendar(actorUserId, tenantSlug, calendarId);
    AvailabilityRule rule = requireRule(calendar.tenantId(), calendarId, ruleId);
    rule.deactivate(clock.instant());
    rules.save(rule);
    cache.invalidateCalendarAfterCommit(calendar.tenantId(), calendar.calendarId());
  }

  private AvailabilityRule requireRule(UUID tenantId, UUID calendarId, UUID ruleId) {
    var tenantRule = rules.findByTenantIdAndId(tenantId, ruleId);
    if (tenantRule.isPresent()) {
      AvailabilityRule rule = tenantRule.orElseThrow();
      if (calendarId.equals(rule.calendarId())) {
        return rule;
      }
      throw new AvailabilityException(
          "availability_rule_not_found", "availability rule was not found");
    }
    if (rules.findById(ruleId).isPresent()) {
      throw new AvailabilityException(
          "availability_access_denied", "cross-tenant access is forbidden");
    }
    throw new AvailabilityException(
        "availability_rule_not_found", "availability rule was not found");
  }

  private static DayOfWeek weekday(int value) {
    if (value < 1 || value > 7) {
      throw new AvailabilityException("validation_error", "weekday must be between 1 and 7");
    }
    return DayOfWeek.of(value);
  }
}
