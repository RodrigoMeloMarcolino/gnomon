package io.gnomon.catalog.application.port.out;

import java.util.UUID;

public interface CatalogTenantAccessPort {

  TenantAccess requireManager(UUID actorUserId, String tenantSlug);

  TenantAccess requireMember(UUID actorUserId, String tenantSlug);

  TenantAccess requirePublicTenant(String tenantSlug);

  UserLink linkStaff(UUID tenantId, String userEmail, java.time.Instant now);

  void unlinkStaff(UUID tenantId, UUID userId);
}
