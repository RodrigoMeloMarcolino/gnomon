package io.gnomon.availability.infrastructure.persistence;

import io.gnomon.availability.domain.AvailabilityRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "availability_rules")
class AvailabilityRuleJpaEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "calendar_id", nullable = false, updatable = false)
  private UUID calendarId;

  @Column(nullable = false)
  private short weekday;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AvailabilityRuleJpaEntity() {}

  static AvailabilityRuleJpaEntity from(AvailabilityRule rule) {
    var entity = new AvailabilityRuleJpaEntity();
    entity.id = rule.id();
    entity.tenantId = rule.tenantId();
    entity.calendarId = rule.calendarId();
    entity.weekday = (short) rule.weekday().getValue();
    entity.startTime = rule.startTime();
    entity.endTime = rule.endTime();
    entity.active = rule.active();
    entity.createdAt = rule.createdAt();
    entity.updatedAt = rule.updatedAt();
    return entity;
  }

  AvailabilityRule toDomain() {
    return new AvailabilityRule(
        id,
        tenantId,
        calendarId,
        DayOfWeek.of(weekday),
        startTime,
        endTime,
        active,
        createdAt,
        updatedAt);
  }
}
