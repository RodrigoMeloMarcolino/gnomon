package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public record UpdateCollaboratorCommand(
    UUID actorUserId, String tenantSlug, UUID collaboratorId, String displayName) {}
