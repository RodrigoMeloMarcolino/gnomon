package io.gnomon.catalog.application.port.in.result;

import io.gnomon.catalog.domain.model.Collaborator;
import java.time.Instant;
import java.util.UUID;

public record CollaboratorResult(
    UUID id,
    UUID tenantId,
    UUID userId,
    String displayName,
    boolean active,
    CalendarResult calendar,
    Instant createdAt,
    Instant updatedAt) {

  public static CollaboratorResult from(Collaborator collaborator, CalendarResult calendar) {
    return new CollaboratorResult(
        collaborator.id(),
        collaborator.tenantId(),
        collaborator.userId(),
        collaborator.displayName(),
        collaborator.active(),
        calendar,
        collaborator.createdAt(),
        collaborator.updatedAt());
  }
}
