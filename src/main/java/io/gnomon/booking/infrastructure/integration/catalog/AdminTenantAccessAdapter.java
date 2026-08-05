package io.gnomon.booking.infrastructure.integration.catalog;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.AdminTenantAccess;
import io.gnomon.booking.application.port.out.AdminTenantAccessPort;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.domain.exception.CatalogException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AdminTenantAccessAdapter implements AdminTenantAccessPort {
  private final CatalogTenantAccessPort access;

  AdminTenantAccessAdapter(CatalogTenantAccessPort access) {
    this.access = access;
  }

  @Override
  public AdminTenantAccess requireMember(UUID actorUserId, String tenantSlug) {
    try {
      var tenant = access.requireMember(actorUserId, tenantSlug);
      return new AdminTenantAccess(tenant.tenantId(), tenant.actorRole());
    } catch (CatalogException exception) {
      throw new BookingException(exception.code(), exception.getMessage());
    }
  }
}
