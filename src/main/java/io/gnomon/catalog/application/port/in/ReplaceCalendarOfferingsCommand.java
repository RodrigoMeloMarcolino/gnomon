package io.gnomon.catalog.application.port.in;

import java.util.Set;
import java.util.UUID;

public record ReplaceCalendarOfferingsCommand(
    UUID actorUserId, String tenantSlug, UUID calendarId, Set<UUID> offeringIds) {}
