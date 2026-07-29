package io.gnomon.catalog.application.port.in;

public interface GetPublicTenantProfileUseCase {

  PublicTenantProfileResult get(String tenantSlug);
}
