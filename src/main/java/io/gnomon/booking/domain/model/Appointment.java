package io.gnomon.booking.domain.model;

import io.gnomon.booking.domain.exception.BookingDomainException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

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

  private static final int SLOT_MINUTES = 15;
  private static final long SLOT_SECONDS = SLOT_MINUTES * 60L;
  private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

  public Appointment {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(tenantId, "tenantId is required");
    Objects.requireNonNull(calendarId, "calendarId is required");
    Objects.requireNonNull(offeringId, "offeringId is required");
    Objects.requireNonNull(customerId, "customerId is required");
    Objects.requireNonNull(startAt, "startAt is required");
    Objects.requireNonNull(endAt, "endAt is required");
    Objects.requireNonNull(status, "status is required");

    validateDuration(durationMinutesSnapshot);
    validateStart(startAt);
    if (!endAt.equals(expectedEnd(startAt, durationMinutesSnapshot))) {
      throw new BookingDomainException(
          "validation_error", "end_at must equal start_at plus duration_minutes_snapshot");
    }

    calendarTimezoneSnapshot =
        requireTimezone(calendarTimezoneSnapshot, "calendar_timezone_snapshot");
    idempotencyKey = requireText(idempotencyKey, "idempotency_key");
    idempotencyFingerprint = requireFingerprint(idempotencyFingerprint, "idempotency_fingerprint");
    customerNotes = normalizeOptional(customerNotes);
  }

  private static void validateDuration(int durationMinutes) {
    if (durationMinutes <= 0 || durationMinutes % SLOT_MINUTES != 0) {
      throw new BookingDomainException(
          "validation_error", "duration_minutes_snapshot must be a positive multiple of 15");
    }
  }

  private static void validateStart(Instant startAt) {
    if (startAt.getNano() != 0 || Math.floorMod(startAt.getEpochSecond(), SLOT_SECONDS) != 0) {
      throw new BookingDomainException(
          "validation_error", "start_at must be aligned to a 15-minute boundary");
    }
  }

  private static Instant expectedEnd(Instant startAt, int durationMinutes) {
    try {
      return startAt.plusSeconds(Math.multiplyExact((long) durationMinutes, 60L));
    } catch (ArithmeticException | DateTimeException exception) {
      throw new BookingDomainException("validation_error", "appointment interval is out of range");
    }
  }

  private static String requireTimezone(String value, String field) {
    String normalized = requireText(value, field);
    try {
      ZoneId.of(normalized);
    } catch (DateTimeException exception) {
      throw new BookingDomainException(
          "validation_error", field + " must be a valid IANA timezone");
    }
    return normalized;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BookingDomainException("validation_error", field + " is required");
    }
    return value.strip();
  }

  private static String requireFingerprint(String value, String field) {
    String normalized = requireText(value, field);
    if (!SHA_256.matcher(normalized).matches()) {
      throw new BookingDomainException(
          "validation_error", field + " must be a lowercase SHA-256 hexadecimal value");
    }
    return normalized;
  }

  private static String normalizeOptional(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip();
  }

  public enum Status {
    SCHEDULED,
    CANCELLED,
    COMPLETED,
    NO_SHOW
  }

  public Appointment cancel() {
    return transitionTo(Status.CANCELLED);
  }

  public Appointment complete() {
    return transitionTo(Status.COMPLETED);
  }

  public Appointment markNoShow() {
    return transitionTo(Status.NO_SHOW);
  }

  private Appointment transitionTo(Status target) {
    if (status != Status.SCHEDULED) {
      throw new BookingDomainException(
          "appointment_status_conflict", "only scheduled appointments can change status");
    }
    return new Appointment(
        id,
        tenantId,
        calendarId,
        offeringId,
        customerId,
        startAt,
        endAt,
        durationMinutesSnapshot,
        calendarTimezoneSnapshot,
        target,
        customerNotes,
        idempotencyKey,
        idempotencyFingerprint);
  }
}
