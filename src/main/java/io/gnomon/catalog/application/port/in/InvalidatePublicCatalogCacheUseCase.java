package io.gnomon.catalog.application.port.in;

import java.util.UUID;

/** Cross-module command boundary for tenant public catalog cache invalidation. */
public interface InvalidatePublicCatalogCacheUseCase {

  void invalidateAfterCommit(UUID tenantId);
}
