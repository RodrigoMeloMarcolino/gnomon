package io.gnomon.availability.infrastructure.persistence;

import io.gnomon.availability.application.port.AvailabilityRuleRepository;
import io.gnomon.availability.domain.AvailabilityException;
import io.gnomon.availability.domain.AvailabilityRule;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class AvailabilityRulePersistenceAdapter implements AvailabilityRuleRepository {

  private final SpringDataAvailabilityRuleRepository repository;

  AvailabilityRulePersistenceAdapter(SpringDataAvailabilityRuleRepository repository) {
    this.repository = repository;
  }

  @Override
  public AvailabilityRule save(AvailabilityRule rule) {
    try {
      return repository.saveAndFlush(AvailabilityRuleJpaEntity.from(rule)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (isKnownValidationConstraint(exception)) {
        throw new AvailabilityException(
            "validation_error", "availability rule violates a constraint");
      }
      throw exception;
    }
  }

  @Override
  public Optional<AvailabilityRule> findById(UUID id) {
    return repository.findById(id).map(AvailabilityRuleJpaEntity::toDomain);
  }

  @Override
  public Optional<AvailabilityRule> findByTenantIdAndId(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id).map(AvailabilityRuleJpaEntity::toDomain);
  }

  @Override
  public List<AvailabilityRule> findByTenantIdAndCalendarId(UUID tenantId, UUID calendarId) {
    return repository
        .findByTenantIdAndCalendarIdOrderByWeekdayAscStartTimeAscIdAsc(tenantId, calendarId)
        .stream()
        .map(AvailabilityRuleJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<AvailabilityRule> findActiveByTenantIdAndCalendarIdAndWeekday(
      UUID tenantId, UUID calendarId, DayOfWeek weekday) {
    return repository
        .findByTenantIdAndCalendarIdAndWeekdayAndActiveTrueOrderByStartTimeAscIdAsc(
            tenantId, calendarId, (short) weekday.getValue())
        .stream()
        .map(AvailabilityRuleJpaEntity::toDomain)
        .toList();
  }

  private static boolean isKnownValidationConstraint(Throwable exception) {
    return contains(exception, "ck_availability_rules_weekday")
        || contains(exception, "ck_availability_rules_time_order")
        || contains(exception, "ck_availability_rules_time_alignment")
        || contains(exception, "fk_availability_rules_tenant")
        || contains(exception, "fk_availability_rules_tenant_calendar");
  }

  private static boolean contains(Throwable exception, String constraint) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
