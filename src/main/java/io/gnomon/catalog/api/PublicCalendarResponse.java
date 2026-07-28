package io.gnomon.catalog.api;

import io.gnomon.catalog.application.PublicCalendarResult;
import java.util.UUID;

public record PublicCalendarResponse(
    UUID id, UUID collaboratorId, String collaboratorName, String name, String timezone) {

  static PublicCalendarResponse from(PublicCalendarResult value) {
    return new PublicCalendarResponse(
        value.id(),
        value.collaboratorId(),
        value.collaboratorName(),
        value.name(),
        value.timezone());
  }
}
