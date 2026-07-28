package io.gnomon.booking.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Appointment(
    UUID id,
    UUID tenantId,
    UUID calendarId,
    UUID offeringId,
    UUID customerId,
    Instant startAt,
    Instant endAt,
    int durationMinutesSnapshot,
    String calendarTimezoneSnapshot,
    Status status,
    String customerNotes,
    String idempotencyKey,
    String idempotencyFingerprint) {

  public Appointment {
    Objects.requireNonNull(id);
    Objects.requireNonNull(tenantId);
    Objects.requireNonNull(calendarId);
    Objects.requireNonNull(offeringId);
    Objects.requireNonNull(customerId);
    Objects.requireNonNull(startAt);
    Objects.requireNonNull(endAt);
    Objects.requireNonNull(calendarTimezoneSnapshot);
    Objects.requireNonNull(status);
    Objects.requireNonNull(idempotencyKey);
    Objects.requireNonNull(idempotencyFingerprint);
  }

  public enum Status {
    SCHEDULED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
  }
}
