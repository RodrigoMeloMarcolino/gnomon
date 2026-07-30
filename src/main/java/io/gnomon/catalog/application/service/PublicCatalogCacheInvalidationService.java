package io.gnomon.catalog.application.service;

import io.gnomon.catalog.application.port.in.InvalidatePublicCatalogCacheUseCase;
import io.gnomon.catalog.application.port.out.PublicCatalogCachePort;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PublicCatalogCacheInvalidationService implements InvalidatePublicCatalogCacheUseCase {

  private final PublicCatalogCachePort cache;

  public PublicCatalogCacheInvalidationService(PublicCatalogCachePort cache) {
    this.cache = cache;
  }

  @Override
  public void invalidateAfterCommit(UUID tenantId) {
    cache.invalidateAfterCommit(tenantId);
  }
}
