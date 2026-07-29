package io.gnomon.availability.application.service;

import io.gnomon.availability.application.port.in.ListAvailableSlotsUseCase;
import io.gnomon.availability.application.port.out.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.out.OccupiedSlotPort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort;
import io.gnomon.availability.domain.service.AvailabilityCalculator;
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
  private final AvailabilityCalculator calculator;
  private final Clock clock;

  @Autowired
  public AvailableSlotsService(
      PublicAvailabilityCatalogPort catalog,
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      AvailabilityCalculator calculator) {
    this(catalog, rules, occupiedSlots, calculator, Clock.systemUTC());
  }

  public AvailableSlotsService(
      PublicAvailabilityCatalogPort catalog,
      AvailabilityRuleRepository rules,
      OccupiedSlotPort occupiedSlots,
      AvailabilityCalculator calculator,
      Clock clock) {
    this.catalog = catalog;
    this.rules = rules;
    this.occupiedSlots = occupiedSlots;
    this.calculator = calculator;
    this.clock = clock;
  }

  @Override
  public List<Instant> list(String tenantSlug, UUID calendarId, UUID offeringId, LocalDate date) {
    var offering = catalog.requireSchedulableOffering(tenantSlug, calendarId, offeringId);
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
