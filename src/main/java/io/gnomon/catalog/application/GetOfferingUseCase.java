package io.gnomon.catalog.application;

import java.util.UUID;

public interface GetOfferingUseCase {

  OfferingResult get(UUID actorUserId, String tenantSlug, UUID offeringId);
}
