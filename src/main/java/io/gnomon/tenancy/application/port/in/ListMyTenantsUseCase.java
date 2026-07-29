package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.TenantResult;
import java.util.List;
import java.util.UUID;

public interface ListMyTenantsUseCase {

  List<TenantResult> list(UUID actorUserId);
}
