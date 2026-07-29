package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public record CreateOfferingCommand(
    UUID actorUserId,
    String tenantSlug,
    String title,
    String description,
    int durationMinutes,
    Integer priceCents) {}
