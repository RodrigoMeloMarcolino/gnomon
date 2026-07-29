package io.gnomon.catalog.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface CatalogTenantAccessPort {

  TenantAccess requireManager(UUID actorUserId, String tenantSlug);

  TenantAccess requireMember(UUID actorUserId, String tenantSlug);

  TenantAccess requirePublicTenant(String tenantSlug);

  UserLink linkStaff(UUID tenantId, String userEmail, Instant now);

  void unlinkStaff(UUID tenantId, UUID userId);

  record TenantAccess(
      UUID tenantId,
      String name,
      String slug,
      String defaultTimezone,
      String currencyCode,
      String actorRole) {}

  record UserLink(UUID userId, String email, String displayName, String membershipRole) {}
}
