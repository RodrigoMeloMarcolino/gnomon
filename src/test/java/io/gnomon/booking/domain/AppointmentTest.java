package io.gnomon.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppointmentTest {

  private static final Instant START = Instant.parse("2026-07-28T12:00:00Z");
  private static final String FINGERPRINT = "a".repeat(64);

  @Test
  void constructor_withValidState_shouldPreserveSnapshotAndNormalizeText() {
    Appointment appointment =
        appointment(START, START.plusSeconds(30 * 60L), 30, " UTC ", " key ", " notes ");

    assertThat(appointment.calendarTimezoneSnapshot()).isEqualTo("UTC");
    assertThat(appointment.idempotencyKey()).isEqualTo("key");
    assertThat(appointment.customerNotes()).isEqualTo("notes");
    assertThat(appointment.status()).isEqualTo(Appointment.Status.SCHEDULED);
  }

  @Test
  void constructor_withBlankOptionalNotes_shouldNormalizeToNull() {
    assertThat(
            appointment(
                    START, START.plusSeconds(30 * 60L), 30, "UTC", "key", " ")
                .customerNotes())
        .isNull();
  }

  @Test
  void constructor_whenEndDoesNotMatchDurationSnapshot_shouldReject() {
    assertValidationError(
        () -> appointment(START, START.plusSeconds(45 * 60L), 30, "UTC", "key", null));
  }

  @Test
  void constructor_withInvalidDurationSnapshot_shouldReject() {
    assertValidationError(
        () -> appointment(START, START.plusSeconds(20 * 60L), 20, "UTC", "key", null));
  }

  @Test
  void constructor_withMisalignedStart_shouldReject() {
    Instant misaligned = Instant.parse("2026-07-28T12:00:01Z");

    assertValidationError(
        () ->
            appointment(
                misaligned, misaligned.plusSeconds(30 * 60L), 30, "UTC", "key", null));
  }

  @Test
  void constructor_withInvalidTimezone_shouldReject() {
    assertValidationError(
        () -> appointment(START, START.plusSeconds(30 * 60L), 30, "Mars/Olympus", "key", null));
  }

  @Test
  void constructor_withBlankIdempotencyKey_shouldReject() {
    assertValidationError(
        () -> appointment(START, START.plusSeconds(30 * 60L), 30, "UTC", " ", null));
  }

  @Test
  void constructor_withMalformedFingerprint_shouldReject() {
    assertThatThrownBy(
            () ->
                new Appointment(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    START,
                    START.plusSeconds(30 * 60L),
                    30,
                    "UTC",
                    Appointment.Status.SCHEDULED,
                    null,
                    "key",
                    "ABC"))
        .isInstanceOf(BookingException.class)
        .hasMessageContaining("lowercase SHA-256");
  }

  private static Appointment appointment(
      Instant startAt,
      Instant endAt,
      int durationMinutes,
      String timezone,
      String idempotencyKey,
      String notes) {
    return new Appointment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        startAt,
        endAt,
        durationMinutes,
        timezone,
        Appointment.Status.SCHEDULED,
        notes,
        idempotencyKey,
        FINGERPRINT);
  }

  private static void assertValidationError(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("validation_error");
  }
}
