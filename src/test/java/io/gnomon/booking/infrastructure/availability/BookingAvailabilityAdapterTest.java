package io.gnomon.booking.infrastructure.integration.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.availability.application.port.out.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.out.OccupiedSlotPort;
import io.gnomon.availability.domain.model.AvailabilityWindow;
import io.gnomon.availability.domain.service.AvailabilityCalculator;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingAvailabilityAdapterTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
  private static final Instant START_AT = Instant.parse("2027-07-01T12:00:00Z");
  private static final Instant NOW = Instant.parse("2027-06-30T12:00:00Z");

  @Mock private AvailabilityRuleRepository rules;
  @Mock private OccupiedSlotPort occupiedSlots;
  @Mock private AvailabilityCalculator calculator;

  @Test
  void isAvailable_shouldCalculateTheCalendarLocalDayWithCurrentOccupiedSlots() {
    var adapter = new BookingAvailabilityAdapter(rules, occupiedSlots, calculator);
    var window =
        new AvailabilityWindow(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), true);
    var rule =
        new io.gnomon.availability.domain.model.AvailabilityRule(
            UUID.randomUUID(),
            TENANT_ID,
            CALENDAR_ID,
            DayOfWeek.THURSDAY,
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            true,
            NOW,
            NOW);
    Instant fromInclusive = Instant.parse("2027-07-01T03:00:00Z");
    Instant toExclusive = Instant.parse("2027-07-02T03:00:00Z");
    Set<Instant> occupied = Set.of(START_AT.plusSeconds(3600));
    when(rules.findActiveByTenantIdAndCalendarIdAndWeekday(
            TENANT_ID, CALENDAR_ID, DayOfWeek.THURSDAY))
        .thenReturn(List.of(rule));
    when(occupiedSlots.findOccupied(TENANT_ID, CALENDAR_ID, fromInclusive, toExclusive))
        .thenReturn(occupied);
    when(calculator.availableStarts(
            List.of(window), 30, occupied, LocalDate.of(2027, 7, 1), ZONE, NOW))
        .thenReturn(List.of(START_AT));

    boolean result = adapter.isAvailable(TENANT_ID, CALENDAR_ID, 30, ZONE, START_AT, NOW);

    assertThat(result).isTrue();
    verify(calculator)
        .availableStarts(List.of(window), 30, occupied, LocalDate.of(2027, 7, 1), ZONE, NOW);
  }

  @Test
  void isAvailable_whenRequestedStartIsNotCalculated_shouldReturnFalse() {
    var adapter = new BookingAvailabilityAdapter(rules, occupiedSlots, calculator);
    when(rules.findActiveByTenantIdAndCalendarIdAndWeekday(
            TENANT_ID, CALENDAR_ID, DayOfWeek.THURSDAY))
        .thenReturn(List.of());
    when(occupiedSlots.findOccupied(
            TENANT_ID,
            CALENDAR_ID,
            Instant.parse("2027-07-01T03:00:00Z"),
            Instant.parse("2027-07-02T03:00:00Z")))
        .thenReturn(Set.of());
    when(calculator.availableStarts(List.of(), 30, Set.of(), LocalDate.of(2027, 7, 1), ZONE, NOW))
        .thenReturn(List.of());

    assertThat(adapter.isAvailable(TENANT_ID, CALENDAR_ID, 30, ZONE, START_AT, NOW)).isFalse();
  }
}
