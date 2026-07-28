package io.gnomon.tenancy.application;

import java.util.UUID;

public interface UpdateTenantUseCase {

  TenantResult update(UpdateTenantCommand command);

  record UpdateTenantCommand(
      UUID actorUserId,
      String tenantSlug,
      String name,
      String timezone,
      String currencyCode,
      String status) {}
}
