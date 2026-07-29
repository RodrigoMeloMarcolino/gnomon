package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.domain.model.Offering.Change;
import java.util.UUID;

public record UpdateOfferingCommand(
    UUID actorUserId,
    String tenantSlug,
    UUID offeringId,
    Change<String> title,
    Change<String> description,
    Change<Integer> durationMinutes,
    Change<Integer> priceCents,
    Change<Boolean> active) {}
