package io.gnomon.catalog.application;

import java.util.UUID;

public interface CreateOfferingUseCase {

  OfferingResult create(CreateOfferingCommand command);

  record CreateOfferingCommand(
      UUID actorUserId,
      String tenantSlug,
      String title,
      String description,
      int durationMinutes,
      Integer priceCents) {}
}
