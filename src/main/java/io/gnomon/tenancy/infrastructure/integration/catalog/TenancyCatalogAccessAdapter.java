package io.gnomon.tenancy.infrastructure.integration.catalog;

import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.TenantAccess;
import io.gnomon.catalog.application.port.out.UserLink;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.tenancy.application.port.out.MembershipRepository;
import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.application.port.out.UserRepository;
import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.TenantMembership.MembershipRole;
import io.gnomon.tenancy.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TenancyCatalogAccessAdapter implements CatalogTenantAccessPort {

  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final UserRepository users;

  TenancyCatalogAccessAdapter(
      TenantRepository tenants, MembershipRepository memberships, UserRepository users) {
    this.tenants = tenants;
    this.memberships = memberships;
    this.users = users;
  }

  @Override
  public TenantAccess requireManager(UUID actorUserId, String tenantSlug) {
    Tenant tenant = tenant(tenantSlug);
    TenantMembership membership = membership(tenant.id(), actorUserId);
    if (membership.role() == MembershipRole.STAFF) {
      throw new CatalogException("insufficient_role", "owner or admin role is required");
    }
    return result(tenant, membership.role().databaseValue());
  }

  @Override
  public TenantAccess requireMember(UUID actorUserId, String tenantSlug) {
    Tenant tenant = tenant(tenantSlug);
    TenantMembership membership = membership(tenant.id(), actorUserId);
    return result(tenant, membership.role().databaseValue());
  }

  @Override
  public TenantAccess requirePublicTenant(String tenantSlug) {
    Tenant tenant = tenant(tenantSlug);
    if (tenant.status() != Tenant.TenantStatus.ACTIVE) {
      throw new CatalogException("tenant_not_found", "tenant was not found");
    }
    return result(tenant, null);
  }

  @Override
  public UserLink linkStaff(UUID tenantId, String userEmail, Instant now) {
    User user =
        users
            .findByEmail(User.normalizeEmail(userEmail))
            .orElseThrow(() -> new CatalogException("user_not_found", "user was not found"));
    TenantMembership membership =
        memberships
            .findByTenantIdAndUserId(tenantId, user.id())
            .orElseGet(
                () ->
                    memberships.createStaffIfAbsent(
                        TenantMembership.staffForCollaborator(tenantId, user.id(), now)));
    return new UserLink(
        user.id(), user.email(), user.displayName(), membership.role().databaseValue());
  }

  @Override
  public void unlinkStaff(UUID tenantId, UUID userId) {
    memberships
        .findByTenantIdAndUserId(tenantId, userId)
        .filter(membership -> membership.role() == MembershipRole.STAFF)
        .ifPresent(memberships::delete);
  }

  private Tenant tenant(String slug) {
    return tenants
        .findBySlug(slug)
        .orElseThrow(() -> new CatalogException("tenant_not_found", "tenant was not found"));
  }

  private TenantMembership membership(UUID tenantId, UUID userId) {
    return memberships
        .findByTenantIdAndUserId(tenantId, userId)
        .orElseThrow(
            () ->
                new CatalogException(
                    "catalog_access_denied", "membership is required for this tenant"));
  }

  private static TenantAccess result(Tenant tenant, String role) {
    return new TenantAccess(
        tenant.id(), tenant.name(), tenant.slug(), tenant.timezone(), tenant.currencyCode(), role);
  }
}
