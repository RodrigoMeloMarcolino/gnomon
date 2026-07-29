package io.gnomon.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.AppointmentFingerprint;
import io.gnomon.booking.application.port.out.NormalizedBooking;
import io.gnomon.booking.infrastructure.fingerprint.DefaultAppointmentFingerprint;
import org.junit.jupiter.api.Test;

class DefaultAppointmentFingerprintTest {

  private static final String CALENDAR_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String OFFERING_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String START_AT = "2026-07-28T12:00:00Z";
  private static final String PHONE = "+5585999999999";

  private final AppointmentFingerprint fingerprint = new DefaultAppointmentFingerprint();

  @Test
  void sha256_withKnownNormalizedBooking_shouldMatchFixedVector() {
    var booking = booking("Alice Smith", PHONE, "alice@example.com", "window seat", START_AT);

    assertThat(fingerprint.sha256(booking))
        .isEqualTo("14898b768a363d7538db3e926552d48ce92e8e3a986692f5e70e4d9e9707da5d");
  }

  @Test
  void sha256_withEquivalentRepresentations_shouldReturnSameFingerprint() {
    var canonical = booking("Alice Smith", PHONE, "alice@example.com", null, START_AT);
    var equivalent =
        new NormalizedBooking(
            "  " + CALENDAR_ID.toUpperCase() + " ",
            OFFERING_ID.toUpperCase(),
            "2026-07-28T14:00:00+02:00",
            "  Alice Smith  ",
            "  " + PHONE + " ",
            " ALICE@EXAMPLE.COM ",
            "  ");

    assertThat(fingerprint.sha256(equivalent)).isEqualTo(fingerprint.sha256(canonical));
  }

  @Test
  void sha256_withDifferentPayloadField_shouldReturnDifferentFingerprint() {
    var original = booking("Alice Smith", PHONE, null, null, START_AT);
    var changed = booking("Alice Smith", PHONE, null, "changed", START_AT);

    assertThat(fingerprint.sha256(changed)).isNotEqualTo(fingerprint.sha256(original));
  }

  @Test
  void sha256_withPotentialDelimiterCollision_shouldRemainUnambiguous() {
    var first = booking("a", PHONE, null, "bc", START_AT);
    var second = booking("ab", PHONE, null, "c", START_AT);

    assertThat(fingerprint.sha256(first)).isNotEqualTo(fingerprint.sha256(second));
  }

  @Test
  void sha256_calledRepeatedly_shouldBeDeterministic() {
    var booking = booking("Alice Smith", PHONE, null, null, START_AT);

    assertThat(fingerprint.sha256(booking)).isEqualTo(fingerprint.sha256(booking));
  }

  @Test
  void sha256_withInvalidRequiredField_shouldReject() {
    var booking = booking("Alice Smith", "85999999999", null, null, START_AT);

    assertThatThrownBy(() -> fingerprint.sha256(booking))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("phone_invalid");
  }

  @Test
  void sha256_withNullInput_shouldReject() {
    assertThatThrownBy(() -> fingerprint.sha256(null))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("validation_error");
  }

  private static NormalizedBooking booking(
      String name, String phone, String email, String notes, String startAt) {
    return new NormalizedBooking(CALENDAR_ID, OFFERING_ID, startAt, name, phone, email, notes);
  }
}
