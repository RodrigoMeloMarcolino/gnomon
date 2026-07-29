package io.gnomon.tenancy.application.port.out;

import io.gnomon.tenancy.domain.model.TenantMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository {

  TenantMembership save(TenantMembership membership);

  TenantMembership createStaffIfAbsent(TenantMembership membership);

  Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId);

  Optional<TenantMembership> findById(UUID id);

  List<TenantMembership> findByUserId(UUID userId);

  List<TenantMembership> findByTenantId(UUID tenantId);

  List<TenantMembership> lockByTenantId(UUID tenantId);

  void delete(TenantMembership membership);
}
