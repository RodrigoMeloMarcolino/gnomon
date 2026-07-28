package io.gnomon.catalog.api;

import io.gnomon.catalog.application.CollaboratorResult;
import java.time.Instant;
import java.util.UUID;

public record CollaboratorResponse(
    UUID id,
    UUID userId,
    String displayName,
    boolean active,
    CalendarResponse calendar,
    Instant createdAt,
    Instant updatedAt) {

  static CollaboratorResponse from(CollaboratorResult value) {
    return new CollaboratorResponse(
        value.id(),
        value.userId(),
        value.displayName(),
        value.active(),
        CalendarResponse.from(value.calendar()),
        value.createdAt(),
        value.updatedAt());
  }
}
