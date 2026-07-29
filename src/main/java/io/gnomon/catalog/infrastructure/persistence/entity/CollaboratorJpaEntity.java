package io.gnomon.catalog.infrastructure.persistence.entity;

import io.gnomon.catalog.domain.model.Collaborator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collaborators")
public class CollaboratorJpaEntity {

  @Id private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id")
  private UUID userId;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected CollaboratorJpaEntity() {}

  public static CollaboratorJpaEntity from(Collaborator value) {
    var entity = new CollaboratorJpaEntity();
    entity.id = value.id();
    entity.tenantId = value.tenantId();
    entity.userId = value.userId();
    entity.displayName = value.displayName();
    entity.active = value.active();
    entity.createdAt = value.createdAt();
    entity.updatedAt = value.updatedAt();
    return entity;
  }

  public Collaborator toDomain() {
    return new Collaborator(id, tenantId, userId, displayName, active, createdAt, updatedAt);
  }
}
