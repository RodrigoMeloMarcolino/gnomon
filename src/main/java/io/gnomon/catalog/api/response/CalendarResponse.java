package io.gnomon.catalog.api.response;

import io.gnomon.catalog.application.port.in.result.CalendarResult;
import java.time.Instant;
import java.util.UUID;

public record CalendarResponse(
    UUID id,
    UUID collaboratorId,
    String name,
    String timezone,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static CalendarResponse from(CalendarResult value) {
    return new CalendarResponse(
        value.id(),
        value.collaboratorId(),
        value.name(),
        value.timezone(),
        value.active(),
        value.createdAt(),
        value.updatedAt());
  }
}
