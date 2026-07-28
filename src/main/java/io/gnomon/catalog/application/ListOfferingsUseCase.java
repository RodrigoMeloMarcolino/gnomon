package io.gnomon.catalog.application;

import java.util.List;
import java.util.UUID;

public interface ListOfferingsUseCase {

  List<OfferingResult> list(UUID actorUserId, String tenantSlug);
}
