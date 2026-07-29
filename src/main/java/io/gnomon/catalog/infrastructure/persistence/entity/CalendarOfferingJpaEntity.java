package io.gnomon.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_offerings")
public class CalendarOfferingJpaEntity {

  @EmbeddedId private CalendarOfferingId id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected CalendarOfferingJpaEntity() {}

  public CalendarOfferingJpaEntity(
      UUID tenantId, UUID calendarId, UUID offeringId, Instant createdAt) {
    this.id = new CalendarOfferingId(calendarId, offeringId);
    this.tenantId = tenantId;
    this.createdAt = createdAt;
  }
}
