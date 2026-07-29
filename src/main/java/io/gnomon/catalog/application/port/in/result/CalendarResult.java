package io.gnomon.catalog.application.port.in.result;

import io.gnomon.catalog.domain.model.Calendar;
import java.time.Instant;
import java.util.UUID;

public record CalendarResult(
    UUID id,
    UUID tenantId,
    UUID collaboratorId,
    String name,
    String timezone,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static CalendarResult from(Calendar calendar) {
    return new CalendarResult(
        calendar.id(),
        calendar.tenantId(),
        calendar.collaboratorId(),
        calendar.name(),
        calendar.timezone(),
        calendar.active(),
        calendar.createdAt(),
        calendar.updatedAt());
  }
}
