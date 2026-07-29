package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.CalendarSummary;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record CalendarResponse(UUID id, String name, String timezone) {
  static CalendarResponse from(CalendarSummary calendar) {
    return new CalendarResponse(calendar.id(), calendar.name(), calendar.timezone());
  }
}
