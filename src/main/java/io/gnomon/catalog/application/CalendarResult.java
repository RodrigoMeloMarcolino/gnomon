package io.gnomon.catalog.application;

import io.gnomon.catalog.domain.Calendar;
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

  static CalendarResult from(Calendar calendar) {
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
