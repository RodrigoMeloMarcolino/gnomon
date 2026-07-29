package io.gnomon.catalog.application.port.out;

import java.util.UUID;

public record TenantAccess(
    UUID tenantId,
    String name,
    String slug,
    String defaultTimezone,
    String currencyCode,
    String actorRole) {}
