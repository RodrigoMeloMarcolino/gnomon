package io.gnomon.tenancy.application.port.in;

import java.util.UUID;

public record AddMembershipCommand(
    UUID actorUserId, String tenantSlug, String userEmail, String role) {}
