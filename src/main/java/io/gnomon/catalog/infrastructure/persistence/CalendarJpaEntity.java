package io.gnomon.catalog.infrastructure.persistence;

import io.gnomon.catalog.domain.Calendar;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendars")
class CalendarJpaEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "collaborator_id", nullable = false, updatable = false)
  private UUID collaboratorId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CalendarJpaEntity() {}

  static CalendarJpaEntity from(Calendar value) {
    var entity = new CalendarJpaEntity();
    entity.id = value.id();
    entity.tenantId = value.tenantId();
    entity.collaboratorId = value.collaboratorId();
    entity.name = value.name();
    entity.timezone = value.timezone();
    entity.active = value.active();
    entity.createdAt = value.createdAt();
    entity.updatedAt = value.updatedAt();
    return entity;
  }

  Calendar toDomain() {
    return new Calendar(id, tenantId, collaboratorId, name, timezone, active, createdAt, updatedAt);
  }
}
