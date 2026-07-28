package io.gnomon.catalog.application;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ReplaceCalendarOfferingsUseCase {

  List<OfferingResult> replace(ReplaceCalendarOfferingsCommand command);

  record ReplaceCalendarOfferingsCommand(
      UUID actorUserId, String tenantSlug, UUID calendarId, Set<UUID> offeringIds) {}
}
