package io.gnomon.tenancy.application.port.in;

import java.util.UUID;

public record ChangeMembershipRoleCommand(
    UUID actorUserId, String tenantSlug, UUID membershipId, String role) {}
