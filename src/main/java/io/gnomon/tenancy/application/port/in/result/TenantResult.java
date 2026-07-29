package io.gnomon.tenancy.application.port.in.result;

import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.domain.model.TenantMembership;
import java.time.Instant;
import java.util.UUID;

public record TenantResult(
    UUID id,
    String name,
    String slug,
    String timezone,
    String currencyCode,
    String status,
    String role,
    Instant createdAt,
    Instant updatedAt) {

  public static TenantResult from(Tenant tenant, TenantMembership membership) {
    return new TenantResult(
        tenant.id(),
        tenant.name(),
        tenant.slug(),
        tenant.timezone(),
        tenant.currencyCode(),
        tenant.status().databaseValue(),
        membership.role().databaseValue(),
        tenant.createdAt(),
        tenant.updatedAt());
  }
}
