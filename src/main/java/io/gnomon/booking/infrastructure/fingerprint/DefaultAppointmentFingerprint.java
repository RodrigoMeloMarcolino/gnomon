package io.gnomon.booking.infrastructure.fingerprint;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.AppointmentFingerprint;
import io.gnomon.booking.application.port.out.AppointmentFingerprint.NormalizedBooking;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DefaultAppointmentFingerprint implements AppointmentFingerprint {

  private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{1,14}");

  @Override
  public String sha256(NormalizedBooking booking) {
    if (booking == null) {
      throw new BookingException("validation_error", "booking fingerprint input is required");
    }

    String canonical =
        encode(canonicalUuid(booking.calendarId(), "calendar_id"))
            + encode(canonicalUuid(booking.offeringId(), "offering_id"))
            + encode(canonicalInstant(booking.startAt()))
            + encode(requiredTrimmed(booking.customerName(), "customer_name"))
            + encode(canonicalPhone(booking.customerPhone()))
            + encode(normalizedEmail(booking.customerEmail()))
            + encode(normalizedOptional(booking.customerNotes()));

    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String canonicalUuid(String value, String field) {
    String normalized = requiredTrimmed(value, field);
    try {
      return UUID.fromString(normalized).toString();
    } catch (IllegalArgumentException exception) {
      throw new BookingException("validation_error", field + " must be a UUID");
    }
  }

  private static String canonicalInstant(String value) {
    String normalized = requiredTrimmed(value, "start_at");
    try {
      return Instant.parse(normalized).toString();
    } catch (DateTimeException exception) {
      throw new BookingException("validation_error", "start_at must be a UTC instant");
    }
  }

  private static String canonicalPhone(String value) {
    String normalized = requiredTrimmed(value, "customer_phone");
    if (!E164.matcher(normalized).matches()) {
      throw new BookingException("phone_invalid", "customer_phone must be canonical E.164");
    }
    return normalized;
  }

  private static String normalizedEmail(String value) {
    return normalizedOptional(value).toLowerCase(Locale.ROOT);
  }

  private static String normalizedOptional(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return value.strip();
  }

  private static String requiredTrimmed(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BookingException("validation_error", field + " is required");
    }
    return value.strip();
  }

  private static String encode(String value) {
    return value.length() + ":" + value;
  }
}
