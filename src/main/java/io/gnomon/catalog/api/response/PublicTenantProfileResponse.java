package io.gnomon.catalog.api.response;

import io.gnomon.catalog.application.port.in.PublicTenantProfileResult;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PublicTenantProfileResponse(
    UUID id, String name, String slug, String timezone, String currencyCode) {

  public static PublicTenantProfileResponse from(PublicTenantProfileResult result) {
    return new PublicTenantProfileResponse(
        result.id(), result.name(), result.slug(), result.timezone(), result.currencyCode());
  }
}
