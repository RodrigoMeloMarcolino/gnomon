package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.OfferingSummary;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record OfferingResponse(UUID id, String title, int durationMinutes, Integer priceCents) {
  static OfferingResponse from(OfferingSummary offering) {
    return new OfferingResponse(
        offering.id(), offering.title(), offering.durationMinutes(), offering.priceCents());
  }
}
