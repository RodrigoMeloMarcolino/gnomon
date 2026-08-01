package io.gnomon.catalog.api.response;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Public integration contract; administrative offering responses remain camelCase. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PublicOfferingResponse(
    UUID id,
    String title,
    String description,
    int durationMinutes,
    Integer priceCents,
    Instant createdAt,
    Instant updatedAt) {

  public static PublicOfferingResponse from(OfferingResult result) {
    return new PublicOfferingResponse(
        result.id(),
        result.title(),
        result.description(),
        result.durationMinutes(),
        result.priceCents(),
        result.createdAt(),
        result.updatedAt());
  }
}
