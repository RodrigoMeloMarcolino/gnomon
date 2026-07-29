package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public interface DeactivateOfferingUseCase {

  void deactivate(UUID actorUserId, String tenantSlug, UUID offeringId);
}
