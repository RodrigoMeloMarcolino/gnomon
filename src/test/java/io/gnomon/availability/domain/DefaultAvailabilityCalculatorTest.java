package io.gnomon.availability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.availability.domain.model.AvailabilityWindow;
import io.gnomon.availability.domain.service.AvailabilityCalculator;
import io.gnomon.availability.domain.service.DefaultAvailabilityCalculator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultAvailabilityCalculatorTest {

  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final LocalDate MONDAY = LocalDate.of(2026, 7, 27);
  private static final Instant BEFORE_MONDAY = Instant.parse("2026-07-26T23:59:59Z");

  private final AvailabilityCalculator calculator = new DefaultAvailabilityCalculator();

  @Test
  void availableStarts_whenServiceExactlyFitsWindow_shouldIncludeStart() {
    var rule = window(DayOfWeek.MONDAY, "09:00", "09:30");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 30, Set.of(), MONDAY, UTC, BEFORE_MONDAY);

    assertThat(result).containsExactly(Instant.parse("2026-07-27T09:00:00Z"));
  }

  @Test
  void availableStarts_whenServiceExceedsRemainingWindow_shouldRemoveCandidate() {
    var rule = window(DayOfWeek.MONDAY, "09:00", "10:00");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 45, Set.of(), MONDAY, UTC, BEFORE_MONDAY);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-07-27T09:00:00Z"), Instant.parse("2026-07-27T09:15:00Z"));
  }

  @Test
  void availableStarts_whenRulesOverlap_shouldDeduplicateAndSortStarts() {
    var laterRule = window(DayOfWeek.MONDAY, "09:30", "10:30");
    var earlierRule = window(DayOfWeek.MONDAY, "09:00", "10:00");

    List<Instant> result =
        calculator.availableStarts(
            List.of(laterRule, earlierRule), 30, Set.of(), MONDAY, UTC, BEFORE_MONDAY);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-07-27T09:00:00Z"),
            Instant.parse("2026-07-27T09:15:00Z"),
            Instant.parse("2026-07-27T09:30:00Z"),
            Instant.parse("2026-07-27T09:45:00Z"),
            Instant.parse("2026-07-27T10:00:00Z"));
  }

  @Test
  void availableStarts_whenAnyServiceSlotIsOccupied_shouldRemoveCandidate() {
    var rule = window(DayOfWeek.MONDAY, "09:00", "10:00");
    var occupied = Set.of(Instant.parse("2026-07-27T09:30:00Z"));

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 30, occupied, MONDAY, UTC, BEFORE_MONDAY);

    assertThat(result).containsExactly(Instant.parse("2026-07-27T09:00:00Z"));
  }

  @Test
  void availableStarts_whenCandidateIsNotAfterNow_shouldRemoveCandidate() {
    var rule = window(DayOfWeek.MONDAY, "09:00", "10:00");
    var now = Instant.parse("2026-07-27T09:15:00Z");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 15, Set.of(), MONDAY, UTC, now);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-07-27T09:30:00Z"), Instant.parse("2026-07-27T09:45:00Z"));
  }

  @Test
  void availableStarts_whenRuleIsInactiveOrForAnotherWeekday_shouldReturnEmpty() {
    var inactive = window(DayOfWeek.MONDAY, "09:00", "10:00", false);
    var tuesday = window(DayOfWeek.TUESDAY, "09:00", "10:00");

    List<Instant> result =
        calculator.availableStarts(
            List.of(inactive, tuesday), 15, Set.of(), MONDAY, UTC, BEFORE_MONDAY);

    assertThat(result).isEmpty();
  }

  @Test
  void availableStarts_whenDateInZoneHasDifferentUtcDate_shouldUseLocalWeekdayAndDate() {
    var zone = ZoneId.of("Pacific/Kiritimati");
    var localMonday = LocalDate.of(2026, 7, 27);
    var rule = window(DayOfWeek.MONDAY, "00:00", "00:30");
    var now = Instant.parse("2026-07-25T00:00:00Z");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 15, Set.of(), localMonday, zone, now);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-07-26T10:00:00Z"), Instant.parse("2026-07-26T10:15:00Z"));
  }

  @Test
  void availableStarts_whenDurationIsInvalid_shouldThrowDomainError() {
    var rule = window(DayOfWeek.MONDAY, "09:00", "10:00");

    assertThatThrownBy(
            () ->
                calculator.availableStarts(List.of(rule), 0, Set.of(), MONDAY, UTC, BEFORE_MONDAY))
        .isInstanceOf(AvailabilityException.class)
        .hasMessage("duration_minutes must be a positive multiple of 15")
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("validation_error");

    assertThatThrownBy(
            () ->
                calculator.availableStarts(List.of(rule), 20, Set.of(), MONDAY, UTC, BEFORE_MONDAY))
        .isInstanceOf(AvailabilityException.class)
        .hasMessage("duration_minutes must be a positive multiple of 15");
  }

  @Test
  void availableStarts_whenLocalTimeIsInDstGap_shouldSkipNonexistentStarts() {
    var zone = ZoneId.of("America/New_York");
    var springForwardSunday = LocalDate.of(2026, 3, 8);
    var rule = window(DayOfWeek.SUNDAY, "01:30", "03:30");
    var now = Instant.parse("2026-03-01T00:00:00Z");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 15, Set.of(), springForwardSunday, zone, now);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-03-08T06:30:00Z"),
            Instant.parse("2026-03-08T06:45:00Z"),
            Instant.parse("2026-03-08T07:00:00Z"),
            Instant.parse("2026-03-08T07:15:00Z"));
  }

  @Test
  void availableStarts_whenLocalTimeIsInDstOverlap_shouldReturnBothInstantsSorted() {
    var zone = ZoneId.of("America/New_York");
    var fallBackSunday = LocalDate.of(2026, 11, 1);
    var rule = window(DayOfWeek.SUNDAY, "01:00", "02:00");
    var now = Instant.parse("2026-10-01T00:00:00Z");

    List<Instant> result =
        calculator.availableStarts(List.of(rule), 15, Set.of(), fallBackSunday, zone, now);

    assertThat(result)
        .containsExactly(
            Instant.parse("2026-11-01T05:00:00Z"),
            Instant.parse("2026-11-01T05:15:00Z"),
            Instant.parse("2026-11-01T05:30:00Z"),
            Instant.parse("2026-11-01T05:45:00Z"),
            Instant.parse("2026-11-01T06:00:00Z"),
            Instant.parse("2026-11-01T06:15:00Z"),
            Instant.parse("2026-11-01T06:30:00Z"),
            Instant.parse("2026-11-01T06:45:00Z"));
  }

  private static AvailabilityWindow window(DayOfWeek weekday, String startTime, String endTime) {
    return window(weekday, startTime, endTime, true);
  }

  private static AvailabilityWindow window(
      DayOfWeek weekday, String startTime, String endTime, boolean active) {
    return new AvailabilityWindow(
        weekday, LocalTime.parse(startTime), LocalTime.parse(endTime), active);
  }
}
