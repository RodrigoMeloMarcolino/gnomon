package io.gnomon.tenancy.infrastructure.integration.catalog;

import io.gnomon.catalog.application.port.in.InvalidatePublicCatalogCacheUseCase;
import io.gnomon.tenancy.application.port.out.TenantPublicCatalogCachePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TenantPublicCatalogCacheAdapter implements TenantPublicCatalogCachePort {

  private final InvalidatePublicCatalogCacheUseCase catalog;

  TenantPublicCatalogCacheAdapter(InvalidatePublicCatalogCacheUseCase catalog) {
    this.catalog = catalog;
  }

  @Override
  public void invalidateAfterCommit(UUID tenantId) {
    catalog.invalidateAfterCommit(tenantId);
  }
}
