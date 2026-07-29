package io.gnomon.catalog.application.port.in;

import java.util.UUID;

public record CreateCollaboratorCommand(UUID actorUserId, String tenantSlug, String displayName) {}
