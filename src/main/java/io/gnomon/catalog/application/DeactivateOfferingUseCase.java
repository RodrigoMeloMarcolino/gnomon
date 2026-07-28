package io.gnomon.catalog.application;

import java.util.UUID;

public interface DeactivateOfferingUseCase {

  void deactivate(UUID actorUserId, String tenantSlug, UUID offeringId);
}
