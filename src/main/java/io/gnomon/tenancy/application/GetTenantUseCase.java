package io.gnomon.tenancy.application;

import java.util.UUID;

public interface GetTenantUseCase {

  TenantResult get(UUID actorUserId, String tenantSlug);
}
