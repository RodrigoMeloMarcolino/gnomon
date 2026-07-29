package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
import java.util.UUID;

public interface GetOfferingUseCase {

  OfferingResult get(UUID actorUserId, String tenantSlug, UUID offeringId);
}
