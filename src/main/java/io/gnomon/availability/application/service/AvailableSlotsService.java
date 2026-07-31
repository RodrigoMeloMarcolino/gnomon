package io.gnomon.availability.application.service;

import io.gnomon.availability.application.port.in.ListAvailableSlotsUseCase;
import io.gnomon.availability.application.port.out.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.out.OccupiedSlotPort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCachePort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort;
import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.availability.domain.service.AvailabilityCalculator;
import io.gnomon.shared.domain.model.BookingHorizon;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AvailableSlotsService implements ListAvailableSlotsUseCase {

  private final PublicAvailabilityCatalogPort catalog;
  private final AvailabilityRuleRepository rules;
  private final OccupiedSlotPort occupiedSlots;
  private final PublicAvailabilityCachePort cache;
  private final AvailabilityCalculator calculator;
  private final BookingHorizon bookingHorizon;
  private final Clock clock;

  @Autowired
  public AvailableSlotsService(
      PublicAvailabilityCatalogPort catalog,
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      PublicAvailabilityCachePort cache,
      AvailabilityCalculator calculator,
      BookingHorizon bookingHorizon) {
    this(catalog, rules, occupiedSlots, cache, calculator, bookingHorizon, Clock.systemUTC());
  }

  public AvailableSlotsService(
      PublicAvailabilityCatalogPort catalog,
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      PublicAvailabilityCachePort cache,
      AvailabilityCalculator calculator,
      Clock clock) {
    this(
        catalog,
        rules,
        occupiedSlots,
        cache,
        calculator,
        new BookingHorizon(BookingHorizon.DEFAULT_MAX_ADVANCE_DAYS),
        clock);
  }

  public AvailableSlotsService(
      PublicAvailabilityCatalogPort catalog,
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      PublicAvailabilityCachePort cache,
      AvailabilityCalculator calculator,
      BookingHorizon bookingHorizon,
      Clock clock) {
    this.catalog = catalog;
    this.rules = rules;
    this.occupiedSlots = occupiedSlots;
    this.cache = cache;
    this.calculator = calculator;
    this.bookingHorizon = bookingHorizon;
    this.clock = clock;
  }

  @Override
  public List<Instant> list(String tenantSlug, UUID calendarId, UUID offeringId, LocalDate date) {
    var offering = catalog.requireSchedulableOffering(tenantSlug, calendarId, offeringId);
    if (!bookingHorizon.allows(date, offering.zoneId(), clock.instant())) {
      throw new AvailabilityException(
          "validation_error", "requested date is beyond the booking horizon");
    }
    return cache.availableSlots(
        offering.tenantId(),
        offering.calendarId(),
        offeringId,
        date,
        () -> calculate(offering, date));
  }

  private List<Instant> calculate(
      io.gnomon.availability.application.port.out.OfferingContext offering, LocalDate date) {
    Instant fromInclusive = date.atStartOfDay(offering.zoneId()).toInstant();
    Instant toExclusive = date.plusDays(1).atStartOfDay(offering.zoneId()).toInstant();
    var windows =
        rules
            .findActiveByTenantIdAndCalendarIdAndWeekday(
                offering.tenantId(), offering.calendarId(), date.getDayOfWeek())
            .stream()
            .map(io.gnomon.availability.domain.model.AvailabilityRule::toWindow)
            .toList();
    var occupied =
        occupiedSlots.findOccupied(
            offering.tenantId(), offering.calendarId(), fromInclusive, toExclusive);
    return calculator.availableStarts(
        windows, offering.durationMinutes(), occupied, date, offering.zoneId(), clock.instant());
  }
}
