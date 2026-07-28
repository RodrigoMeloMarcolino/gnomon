package io.gnomon.catalog.application;

import io.gnomon.catalog.domain.Offering.Change;
import java.util.UUID;

public interface UpdateOfferingUseCase {

  OfferingResult update(UpdateOfferingCommand command);

  record UpdateOfferingCommand(
      UUID actorUserId,
      String tenantSlug,
      UUID offeringId,
      Change<String> title,
      Change<String> description,
      Change<Integer> durationMinutes,
      Change<Integer> priceCents,
      Change<Boolean> active) {}
}
