package io.gnomon.tenancy.application.port.in;

import java.util.UUID;

public record RemoveMembershipCommand(UUID actorUserId, String tenantSlug, UUID membershipId) {}
