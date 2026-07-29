package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public record LinkCollaboratorUserCommand(
    UUID actorUserId, String tenantSlug, UUID collaboratorId, String userEmail) {}
