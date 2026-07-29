package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReplaceCalendarOfferingsUseCase {

  List<OfferingResult> replace(ReplaceCalendarOfferingsCommand command);

  record ReplaceCalendarOfferingsCommand(
      UUID actorUserId, String tenantSlug, UUID calendarId, Set<UUID> offeringIds) {}
}
