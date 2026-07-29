package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
import java.util.List;
import java.util.UUID;

public interface ListOfferingsUseCase {

  List<OfferingResult> list(UUID actorUserId, String tenantSlug);
}
