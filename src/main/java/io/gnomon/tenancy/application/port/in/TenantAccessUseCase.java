package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.TenantAccessResult;
import java.util.UUID;

/** Cross-module input port for tenant membership and role authorization. */
public interface TenantAccessUseCase {
  TenantAccessResult requireMember(UUID actorUserId, String tenantSlug);
}
