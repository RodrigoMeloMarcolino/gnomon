package io.gnomon.tenancy.application.port.in;

import java.util.UUID;

public record UpdateTenantCommand(
    UUID actorUserId,
    String tenantSlug,
    String name,
    String timezone,
    String currencyCode,
    String status) {}
