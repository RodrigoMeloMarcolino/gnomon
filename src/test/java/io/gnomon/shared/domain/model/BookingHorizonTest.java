package io.gnomon.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class BookingHorizonTest {

  private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

  @Test
  void allowsExactBoundary() {
    var horizon = new BookingHorizon(180);
    assertThat(horizon.allows(NOW.plusSeconds(180L * 24 * 60 * 60), NOW)).isTrue();
  }

  @Test
  void rejectsAfterBoundary() {
    var horizon = new BookingHorizon(180);
    assertThat(horizon.allows(NOW.plusSeconds(180L * 24 * 60 * 60 + 1), NOW)).isFalse();
  }

  @Test
  void evaluatesLocalDateUsingCalendarTimezone() {
    var horizon = new BookingHorizon(1);
    Instant localMidnightNow = NOW;
    assertThat(
            horizon.allows(
                LocalDate.of(2027, 1, 1), ZoneId.of("America/Fortaleza"), localMidnightNow))
        .isTrue();
    assertThat(
            horizon.allows(
                LocalDate.of(2027, 1, 2), ZoneId.of("America/Fortaleza"), localMidnightNow))
        .isFalse();
  }
}
