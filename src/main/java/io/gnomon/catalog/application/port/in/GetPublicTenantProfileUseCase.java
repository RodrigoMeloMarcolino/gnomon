package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public interface GetPublicTenantProfileUseCase {

  PublicTenantProfileResult get(String tenantSlug);

  record PublicTenantProfileResult(
      UUID id, String name, String slug, String timezone, String currencyCode) {}
}
