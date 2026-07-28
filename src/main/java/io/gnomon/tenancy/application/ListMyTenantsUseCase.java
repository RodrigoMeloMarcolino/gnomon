package io.gnomon.tenancy.application;

import java.util.List;
import java.util.UUID;

public interface ListMyTenantsUseCase {

  List<TenantResult> list(UUID actorUserId);
}
