package io.gnomon.catalog.application;

import io.gnomon.catalog.domain.Offering;
import java.time.Instant;
import java.util.UUID;

public record OfferingResult(
    UUID id,
    UUID tenantId,
    String title,
    String description,
    int durationMinutes,
    Integer priceCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static OfferingResult from(Offering offering) {
    return new OfferingResult(
        offering.id(),
        offering.tenantId(),
        offering.title(),
        offering.description(),
        offering.durationMinutes(),
        offering.priceCents(),
        offering.active(),
        offering.createdAt(),
        offering.updatedAt());
  }
}
