package io.gnomon.tenancy.application.service;

import io.gnomon.tenancy.application.port.in.TenantAccessUseCase;
import io.gnomon.tenancy.application.port.in.result.TenantAccessResult;
import io.gnomon.tenancy.application.port.out.MembershipRepository;
import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.TenantMembership;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TenantAccessService implements TenantAccessUseCase {
  private final TenantRepository tenants;
  private final MembershipRepository memberships;

  public TenantAccessService(TenantRepository tenants, MembershipRepository memberships) {
    this.tenants = tenants;
    this.memberships = memberships;
  }

  @Override
  public TenantAccessResult requireMember(UUID actorUserId, String tenantSlug) {
    UUID tenantId =
        tenants
            .findBySlug(tenantSlug)
            .orElseThrow(() -> new TenancyException("tenant_not_found", "tenant was not found"))
            .id();
    TenantMembership membership =
        memberships
            .findByTenantIdAndUserId(tenantId, actorUserId)
            .orElseThrow(
                () ->
                    new TenancyException(
                        "membership_required", "membership is required for this tenant"));
    return new TenantAccessResult(tenantId, membership.role().databaseValue());
  }
}
