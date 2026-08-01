package io.gnomon.catalog.api.response;

import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record PublicCalendarResponse(
    UUID id, UUID collaboratorId, String collaboratorName, String name, String timezone) {

  public static PublicCalendarResponse from(PublicCalendarResult value) {
    return new PublicCalendarResponse(
        value.id(),
        value.collaboratorId(),
        value.collaboratorName(),
        value.name(),
        value.timezone());
  }
}
