package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
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
