package io.gnomon.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.booking.domain.exception.BookingDomainException;
import io.gnomon.booking.domain.service.DefaultSlotGenerator;
import io.gnomon.booking.domain.service.SlotGenerator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultSlotGeneratorTest {

  private static final Instant START = Instant.parse("2026-07-28T12:00:00Z");

  private final SlotGenerator generator = new DefaultSlotGenerator();

  @ParameterizedTest
  @MethodSource("validDurations")
  void generate_withValidDuration_shouldReturnEveryQuarterHour(
      int durationMinutes, int expectedCount, Instant expectedLast) {
    List<Instant> result = generator.generate(START, durationMinutes);

    assertThat(result).hasSize(expectedCount);
    assertThat(result).startsWith(START).endsWith(expectedLast);
    assertThat(result)
        .zipSatisfy(
            expectedSlots(expectedCount),
            (actual, expected) -> assertThat(actual).isEqualTo(expected));
  }

  @Test
  void generate_duringDstOverlap_shouldOperateOnDistinctInstants() {
    ZoneId newYork = ZoneId.of("America/New_York");
    ZonedDateTime firstOneThirty =
        ZonedDateTime.parse("2026-11-01T01:30:00-04:00[America/New_York]");
    ZonedDateTime secondOneThirty = firstOneThirty.withLaterOffsetAtOverlap();

    assertThat(generator.generate(firstOneThirty.toInstant(), 30))
        .containsExactly(
            Instant.parse("2026-11-01T05:30:00Z"), Instant.parse("2026-11-01T05:45:00Z"));
    assertThat(generator.generate(secondOneThirty.toInstant(), 30))
        .containsExactly(
            Instant.parse("2026-11-01T06:30:00Z"), Instant.parse("2026-11-01T06:45:00Z"));
    assertThat(firstOneThirty.getZone()).isEqualTo(newYork);
  }

  @ParameterizedTest
  @ValueSource(ints = {-15, 0, 1, 14, 16, 29})
  void generate_withInvalidDuration_shouldReject(int durationMinutes) {
    assertThatThrownBy(() -> generator.generate(START, durationMinutes))
        .isInstanceOf(BookingDomainException.class)
        .extracting(exception -> ((BookingDomainException) exception).code())
        .isEqualTo("validation_error");
  }

  @ParameterizedTest
  @MethodSource("misalignedStarts")
  void generate_withMisalignedStart_shouldReject(Instant startAt) {
    assertThatThrownBy(() -> generator.generate(startAt, 30))
        .isInstanceOf(BookingDomainException.class)
        .hasMessageContaining("15-minute boundary");
  }

  @Test
  void generate_withNullStart_shouldReject() {
    assertThatThrownBy(() -> generator.generate(null, 30))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("startAt is required");
  }

  private static Stream<Arguments> validDurations() {
    return Stream.of(
        Arguments.of(30, 2, Instant.parse("2026-07-28T12:15:00Z")),
        Arguments.of(45, 3, Instant.parse("2026-07-28T12:30:00Z")),
        Arguments.of(60, 4, Instant.parse("2026-07-28T12:45:00Z")),
        Arguments.of(240, 16, Instant.parse("2026-07-28T15:45:00Z")));
  }

  private static List<Instant> expectedSlots(int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(index -> START.plusSeconds(index * 15L * 60L))
        .toList();
  }

  private static Stream<Instant> misalignedStarts() {
    return Stream.of(
        Instant.parse("2026-07-28T12:01:00Z"),
        Instant.parse("2026-07-28T12:00:01Z"),
        Instant.parse("2026-07-28T12:00:00.000000001Z"));
  }
}
