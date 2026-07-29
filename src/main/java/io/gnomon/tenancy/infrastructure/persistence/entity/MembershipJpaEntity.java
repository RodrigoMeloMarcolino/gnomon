package io.gnomon.tenancy.infrastructure.persistence.entity;

import io.gnomon.tenancy.domain.model.TenantMembership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant_memberships")
public class MembershipJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false)
  private String role;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected MembershipJpaEntity() {}

  public static MembershipJpaEntity from(TenantMembership membership) {
    MembershipJpaEntity entity = new MembershipJpaEntity();
    entity.id = membership.id();
    entity.tenantId = membership.tenantId();
    entity.userId = membership.userId();
    entity.role = membership.role().databaseValue();
    entity.createdAt = membership.createdAt();
    entity.updatedAt = membership.updatedAt();
    return entity;
  }

  public TenantMembership toDomain() {
    return new TenantMembership(
        id, tenantId, userId, TenantMembership.MembershipRole.from(role), createdAt, updatedAt);
  }

  public UUID tenantId() {
    return tenantId;
  }
}
