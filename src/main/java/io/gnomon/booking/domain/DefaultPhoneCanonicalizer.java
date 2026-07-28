package io.gnomon.booking.domain;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.Locale;
import java.util.regex.Pattern;

public final class DefaultPhoneCanonicalizer implements PhoneCanonicalizer {

  private static final Pattern ACCEPTED_INPUT = Pattern.compile("\\+?[\\d ()\\-]+");
  private static final PhoneNumberUtil PHONE_NUMBER_UTIL = PhoneNumberUtil.getInstance();

  private final String defaultRegion;

  public DefaultPhoneCanonicalizer(String defaultRegion) {
    if (defaultRegion == null || defaultRegion.isBlank()) {
      throw new IllegalArgumentException("default phone region is required");
    }
    String normalized = defaultRegion.strip().toUpperCase(Locale.ROOT);
    if (!PHONE_NUMBER_UTIL.getSupportedRegions().contains(normalized)) {
      throw new IllegalArgumentException("unsupported default phone region: " + normalized);
    }
    this.defaultRegion = normalized;
  }

  @Override
  public String canonicalize(String raw) {
    if (raw == null || raw.isBlank() || !ACCEPTED_INPUT.matcher(raw).matches()) {
      throw invalidPhone();
    }
    try {
      var parsed = PHONE_NUMBER_UTIL.parse(raw, defaultRegion);
      if (!PHONE_NUMBER_UTIL.isPossibleNumber(parsed) || !PHONE_NUMBER_UTIL.isValidNumber(parsed)) {
        throw invalidPhone();
      }
      return PHONE_NUMBER_UTIL.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
    } catch (NumberParseException exception) {
      throw invalidPhone();
    }
  }

  private static BookingException invalidPhone() {
    return new BookingException("phone_invalid", "customer_phone is invalid");
  }
}
