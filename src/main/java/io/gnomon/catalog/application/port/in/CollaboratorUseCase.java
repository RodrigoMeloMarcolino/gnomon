package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.CollaboratorResult;
import java.util.List;
import java.util.UUID;

public interface CollaboratorUseCase {

  CollaboratorResult create(CreateCollaboratorCommand command);

  List<CollaboratorResult> list(UUID actorUserId, String tenantSlug);

  CollaboratorResult get(UUID actorUserId, String tenantSlug, UUID collaboratorId);

  CollaboratorResult update(UpdateCollaboratorCommand command);

  void deactivate(UUID actorUserId, String tenantSlug, UUID collaboratorId);

  CollaboratorResult linkUser(LinkCollaboratorUserCommand command);

  CollaboratorResult unlinkUser(UUID actorUserId, String tenantSlug, UUID collaboratorId);

  record CreateCollaboratorCommand(UUID actorUserId, String tenantSlug, String displayName) {}

  record UpdateCollaboratorCommand(
      UUID actorUserId, String tenantSlug, UUID collaboratorId, String displayName) {}

  record LinkCollaboratorUserCommand(
      UUID actorUserId, String tenantSlug, UUID collaboratorId, String userEmail) {}
}
