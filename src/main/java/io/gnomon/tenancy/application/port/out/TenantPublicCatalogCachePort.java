package io.gnomon.tenancy.application.port.out;

import java.util.UUID;

/** Invalidates the catalog-owned public profile after a tenant update commits. */
public interface TenantPublicCatalogCachePort {

  void invalidateAfterCommit(UUID tenantId);
}
