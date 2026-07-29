package io.gnomon.tenancy.infrastructure.persistence.entity;

import io.gnomon.tenancy.domain.model.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tenants")
public class TenantJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true, updatable = false)
  private String slug;

  @Column(nullable = false)
  private String timezone;

  @Column(name = "currency_code", nullable = false, columnDefinition = "char(3)")
  @JdbcTypeCode(SqlTypes.CHAR)
  private String currencyCode;

  @Column(nullable = false)
  private String status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantJpaEntity() {}

  public static TenantJpaEntity from(Tenant tenant) {
    TenantJpaEntity entity = new TenantJpaEntity();
    entity.id = tenant.id();
    entity.name = tenant.name();
    entity.slug = tenant.slug();
    entity.timezone = tenant.timezone();
    entity.currencyCode = tenant.currencyCode();
    entity.status = tenant.status().databaseValue();
    entity.createdAt = tenant.createdAt();
    entity.updatedAt = tenant.updatedAt();
    return entity;
  }

  public Tenant toDomain() {
    return new Tenant(
        id,
        name,
        slug,
        timezone,
        currencyCode.strip(),
        Tenant.TenantStatus.from(status),
        createdAt,
        updatedAt);
  }
}
