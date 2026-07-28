package io.gnomon.tenancy.application;

import java.util.UUID;

public interface CreateTenantUseCase {

  TenantResult create(CreateTenantCommand command);

  record CreateTenantCommand(
      UUID actorUserId, String name, String slug, String timezone, String currencyCode) {}
}
