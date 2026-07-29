package io.gnomon.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.PhoneCanonicalizer;
import io.gnomon.booking.infrastructure.phone.DefaultPhoneCanonicalizer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultPhoneCanonicalizerTest {

  private final PhoneCanonicalizer brazilian = new DefaultPhoneCanonicalizer("BR");

  @ParameterizedTest
  @MethodSource("validBrazilianInputs")
  void canonicalize_withSupportedFormatting_shouldReturnE164(String raw, String expected) {
    assertThat(brazilian.canonicalize(raw)).isEqualTo(expected);
  }

  @Test
  void canonicalize_withNationalNumber_shouldUseConfiguredDefaultRegion() {
    PhoneCanonicalizer american = new DefaultPhoneCanonicalizer("us");

    assertThat(american.canonicalize("(415) 555-2671")).isEqualTo("+14155552671");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "123", "+55 85 PHONE", "+55/85/99999/9999", "++55 85 99999-9999"})
  void canonicalize_withInvalidInput_shouldThrowPhoneInvalid(String raw) {
    assertThatThrownBy(() -> brazilian.canonicalize(raw))
        .isInstanceOf(BookingException.class)
        .hasMessage("customer_phone is invalid")
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("phone_invalid");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "ZZ", "BRA"})
  void constructor_withInvalidRegion_shouldRejectConfiguration(String region) {
    assertThatThrownBy(() -> new DefaultPhoneCanonicalizer(region))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Stream<Arguments> validBrazilianInputs() {
    return Stream.of(
        Arguments.of("(85) 99999-9999", "+5585999999999"),
        Arguments.of("85 99999-9999", "+5585999999999"),
        Arguments.of("+55 (85) 99999-9999", "+5585999999999"),
        Arguments.of("+1 (415) 555-2671", "+14155552671"));
  }
}
