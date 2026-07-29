package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.TenantResult;
import java.util.UUID;

public interface GetTenantUseCase {

  TenantResult get(UUID actorUserId, String tenantSlug);
}
