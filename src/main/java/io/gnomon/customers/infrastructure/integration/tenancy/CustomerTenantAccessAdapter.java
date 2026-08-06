package io.gnomon.customers.infrastructure.integration.tenancy;

import io.gnomon.customers.application.port.out.CustomerTenantAccess;
import io.gnomon.customers.application.port.out.CustomerTenantAccessPort;
import io.gnomon.customers.domain.exception.CustomerException;
import io.gnomon.tenancy.application.port.in.TenantAccessUseCase;
import io.gnomon.tenancy.domain.exception.TenancyException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CustomerTenantAccessAdapter implements CustomerTenantAccessPort {
  private final TenantAccessUseCase tenancy;

  CustomerTenantAccessAdapter(TenantAccessUseCase tenancy) {
    this.tenancy = tenancy;
  }

  @Override
  public CustomerTenantAccess requireManager(UUID actorUserId, String tenantSlug) {
    try {
      var access = tenancy.requireMember(actorUserId, tenantSlug);
      if ("staff".equals(access.role())) {
        throw new CustomerException("insufficient_role", "owner or admin role is required");
      }
      return new CustomerTenantAccess(access.tenantId());
    } catch (TenancyException exception) {
      throw new CustomerException(exception.code(), exception.getMessage());
    }
  }
}
