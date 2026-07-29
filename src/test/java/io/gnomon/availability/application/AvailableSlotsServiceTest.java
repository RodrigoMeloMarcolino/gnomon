package io.gnomon.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.availability.application.port.out.AvailabilityRuleRepository;
import io.gnomon.availability.application.port.out.OccupiedSlotPort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort.OfferingContext;
import io.gnomon.availability.application.service.AvailableSlotsService;
import io.gnomon.availability.domain.model.AvailabilityRule;
import io.gnomon.availability.domain.service.AvailabilityCalculator;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailableSlotsServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
  private static final LocalDate DATE = LocalDate.of(2027, 7, 1);
  private static final Instant NOW = Instant.parse("2027-07-01T10:00:00Z");

  @Mock private PublicAvailabilityCatalogPort catalog;
  @Mock private AvailabilityRuleRepository rules;
  @Mock private OccupiedSlotPort occupiedSlots;
  @Mock private AvailabilityCalculator calculator;

  @Test
  void list_whenOfferingIsSchedulable_shouldCalculateUsingLocalDayAndInjectedClock() {
    var service =
        new AvailableSlotsService(
            catalog, rules, occupiedSlots, calculator, Clock.fixed(NOW, ZoneOffset.UTC));
    var context = new OfferingContext(TENANT_ID, CALENDAR_ID, ZONE, 30);
    var rule =
        new AvailabilityRule(
            UUID.randomUUID(),
            TENANT_ID,
            CALENDAR_ID,
            DayOfWeek.THURSDAY,
            LocalTime.of(9, 0),
            LocalTime.of(10, 0),
            true,
            NOW,
            NOW);
    Instant occupied = Instant.parse("2027-07-01T12:00:00Z");
    Instant available = Instant.parse("2027-07-01T12:30:00Z");
    Instant fromInclusive = Instant.parse("2027-07-01T03:00:00Z");
    Instant toExclusive = Instant.parse("2027-07-02T03:00:00Z");

    when(catalog.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .thenReturn(context);
    when(rules.findActiveByTenantIdAndCalendarIdAndWeekday(
            TENANT_ID, CALENDAR_ID, DayOfWeek.THURSDAY))
        .thenReturn(List.of(rule));
    when(occupiedSlots.findOccupied(TENANT_ID, CALENDAR_ID, fromInclusive, toExclusive))
        .thenReturn(Set.of(occupied));
    when(calculator.availableStarts(
            List.of(rule.toWindow()), 30, Set.of(occupied), DATE, ZONE, NOW))
        .thenReturn(List.of(available));

    List<Instant> result = service.list("barbearia-solar", CALENDAR_ID, OFFERING_ID, DATE);

    assertThat(result).containsExactly(available);
    verify(occupiedSlots).findOccupied(TENANT_ID, CALENDAR_ID, fromInclusive, toExclusive);
    verify(calculator)
        .availableStarts(List.of(rule.toWindow()), 30, Set.of(occupied), DATE, ZONE, NOW);
  }
}
