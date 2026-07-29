package io.gnomon.tenancy.application.port.in;

import java.util.UUID;

public record CreateTenantCommand(
    UUID actorUserId, String name, String slug, String timezone, String currencyCode) {}
