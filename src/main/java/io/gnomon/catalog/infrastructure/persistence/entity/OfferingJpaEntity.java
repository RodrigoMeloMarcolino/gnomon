package io.gnomon.catalog.infrastructure.persistence.entity;

import io.gnomon.catalog.domain.model.Offering;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "offerings")
public class OfferingJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(nullable = false, length = 120)
  private String title;

  @Column private String description;

  @Column(name = "duration_minutes", nullable = false)
  private int durationMinutes;

  @Column(name = "price_cents")
  private Integer priceCents;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected OfferingJpaEntity() {}

  public static OfferingJpaEntity from(Offering offering) {
    OfferingJpaEntity entity = new OfferingJpaEntity();
    entity.id = offering.id();
    entity.tenantId = offering.tenantId();
    entity.title = offering.title();
    entity.description = offering.description();
    entity.durationMinutes = offering.durationMinutes();
    entity.priceCents = offering.priceCents();
    entity.active = offering.active();
    entity.createdAt = offering.createdAt();
    entity.updatedAt = offering.updatedAt();
    return entity;
  }

  public Offering toDomain() {
    return new Offering(
        id,
        tenantId,
        title,
        description,
        durationMinutes,
        priceCents,
        active,
        createdAt,
        updatedAt);
  }
}
