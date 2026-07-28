package io.gnomon.catalog.api;

import io.gnomon.catalog.application.GetPublicTenantProfileUseCase.PublicTenantProfileResult;
import java.util.UUID;

public record PublicTenantProfileResponse(
    UUID id, String name, String slug, String timezone, String currencyCode) {

  public static PublicTenantProfileResponse from(PublicTenantProfileResult result) {
    return new PublicTenantProfileResponse(
        result.id(), result.name(), result.slug(), result.timezone(), result.currencyCode());
  }
}
