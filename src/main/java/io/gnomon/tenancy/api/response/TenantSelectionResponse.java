package io.gnomon.tenancy.api.response;

import io.gnomon.tenancy.application.port.in.result.TenantResult;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Minimal snake_case tenant selector used by Umbra's authenticated bootstrap. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TenantSelectionResponse(
    UUID id, String name, String slug, String timezone, String currencyCode, String role) {

  public static TenantSelectionResponse from(TenantResult result) {
    return new TenantSelectionResponse(
        result.id(),
        result.name(),
        result.slug(),
        result.timezone(),
        result.currencyCode(),
        result.role());
  }
}
