package io.gnomon.booking.infrastructure.integration.tenancy;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.AdminTenantAccess;
import io.gnomon.booking.application.port.out.AdminTenantAccessPort;
import io.gnomon.tenancy.application.port.in.TenantAccessUseCase;
import io.gnomon.tenancy.domain.exception.TenancyException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingTenantAccessAdapter implements AdminTenantAccessPort {
  private final TenantAccessUseCase tenancy;

  BookingTenantAccessAdapter(TenantAccessUseCase tenancy) {
    this.tenancy = tenancy;
  }

  @Override
  public AdminTenantAccess requireMember(UUID actorUserId, String tenantSlug) {
    try {
      var access = tenancy.requireMember(actorUserId, tenantSlug);
      return new AdminTenantAccess(access.tenantId(), access.role());
    } catch (TenancyException exception) {
      throw new BookingException(exception.code(), exception.getMessage());
    }
  }
}
