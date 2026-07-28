package io.gnomon.booking.infrastructure.availability;

import io.gnomon.availability.application.port.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.OccupiedSlotPort;
import io.gnomon.availability.domain.AvailabilityCalculator;
import io.gnomon.booking.application.port.BookingAvailabilityPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingAvailabilityAdapter implements BookingAvailabilityPort {

  private final AvailabilityRuleRepository rules;
  private final OccupiedSlotPort occupiedSlots;
  private final AvailabilityCalculator calculator;

  BookingAvailabilityAdapter(
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      AvailabilityCalculator calculator) {
    this.rules = rules;
    this.occupiedSlots = occupiedSlots;
    this.calculator = calculator;
  }

  @Override
  public boolean isAvailable(
      UUID tenantId,
      UUID calendarId,
      int durationMinutes,
      ZoneId zoneId,
      Instant startAt,
      Instant now) {
    LocalDate localDate = startAt.atZone(zoneId).toLocalDate();
    Instant fromInclusive = localDate.atStartOfDay(zoneId).toInstant();
    Instant toExclusive = localDate.plusDays(1).atStartOfDay(zoneId).toInstant();
    var windows =
        rules
            .findActiveByTenantIdAndCalendarIdAndWeekday(
                tenantId, calendarId, localDate.getDayOfWeek())
            .stream()
            .map(io.gnomon.availability.domain.AvailabilityRule::toWindow)
            .toList();
    var occupied = occupiedSlots.findOccupied(tenantId, calendarId, fromInclusive, toExclusive);
    return calculator
        .availableStarts(windows, durationMinutes, occupied, localDate, zoneId, now)
        .contains(startAt);
  }
}
