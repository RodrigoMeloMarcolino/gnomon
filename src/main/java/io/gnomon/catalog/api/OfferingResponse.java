package io.gnomon.catalog.api;

import io.gnomon.catalog.application.OfferingResult;
import java.time.Instant;
import java.util.UUID;

public record OfferingResponse(
    UUID id,
    String title,
    String description,
    int durationMinutes,
    Integer priceCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static OfferingResponse from(OfferingResult result) {
    return new OfferingResponse(
        result.id(),
        result.title(),
        result.description(),
        result.durationMinutes(),
        result.priceCents(),
        result.active(),
        result.createdAt(),
        result.updatedAt());
  }
}
