package io.gnomon.availability.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.availability.application.AvailabilityRuleUseCase.CreateAvailabilityRuleCommand;
import io.gnomon.availability.application.AvailabilityRuleUseCase.UpdateAvailabilityRuleCommand;
import io.gnomon.availability.application.port.AvailabilityCalendarAccessPort;
import io.gnomon.availability.application.port.AvailabilityCalendarAccessPort.CalendarContext;
import io.gnomon.availability.application.port.AvailabilityRuleRepository;
import io.gnomon.availability.domain.AvailabilityException;
import io.gnomon.availability.domain.AvailabilityRule;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityRuleServiceTest {

  private static final Instant NOW = Instant.parse("2027-07-01T12:00:00Z");
  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID CALENDAR_ID = UUID.randomUUID();

  @Mock private AvailabilityRuleRepository rules;
  @Mock private AvailabilityCalendarAccessPort calendarAccess;

  private AvailabilityRuleService service;

  @BeforeEach
  void setUp() {
    service = new AvailabilityRuleService(rules, calendarAccess, Clock.fixed(NOW, ZoneOffset.UTC));
    when(calendarAccess.requireWritableCalendar(ACTOR_ID, "tenant", CALENDAR_ID))
        .thenReturn(new CalendarContext(TENANT_ID, CALENDAR_ID, ZoneId.of("America/Fortaleza")));
  }

  @Test
  void create_whenWindowIsValid_shouldPersistTenantScopedActiveRule() {
    when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AvailabilityRuleResult result =
        service.create(
            new CreateAvailabilityRuleCommand(
                ACTOR_ID, "tenant", CALENDAR_ID, 1, LocalTime.of(9, 0), LocalTime.of(12, 0)));

    assertThat(result.weekday()).isEqualTo(1);
    assertThat(result.active()).isTrue();
    assertThat(result.createdAt()).isEqualTo(NOW);
  }

  @Test
  void create_whenWeekdayIsInvalid_shouldReturnStableValidationError() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreateAvailabilityRuleCommand(
                        ACTOR_ID,
                        "tenant",
                        CALENDAR_ID,
                        8,
                        LocalTime.of(9, 0),
                        LocalTime.of(12, 0))))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("validation_error");
  }

  @Test
  void update_whenPartialTimesInvertWindow_shouldRejectWithoutSaving() {
    AvailabilityRule rule = rule();
    when(rules.findByTenantIdAndId(TENANT_ID, rule.id())).thenReturn(Optional.of(rule));

    assertThatThrownBy(
            () ->
                service.update(
                    new UpdateAvailabilityRuleCommand(
                        ACTOR_ID,
                        "tenant",
                        CALENDAR_ID,
                        rule.id(),
                        null,
                        LocalTime.of(13, 0),
                        null,
                        null)))
        .isInstanceOf(AvailabilityException.class);
    assertThat(rule.startTime()).isEqualTo(LocalTime.of(9, 0));
  }

  @Test
  void deactivate_whenRuleExists_shouldSoftDeactivateAndSave() {
    AvailabilityRule rule = rule();
    when(rules.findByTenantIdAndId(TENANT_ID, rule.id())).thenReturn(Optional.of(rule));
    when(rules.save(rule)).thenReturn(rule);

    service.deactivate(ACTOR_ID, "tenant", CALENDAR_ID, rule.id());

    assertThat(rule.active()).isFalse();
    verify(rules).save(rule);
  }

  @Test
  void get_whenRuleBelongsToAnotherCalendarInTenant_shouldReturnNotFound() {
    AvailabilityRule rule =
        AvailabilityRule.create(
            TENANT_ID,
            UUID.randomUUID(),
            DayOfWeek.MONDAY,
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            NOW);
    when(rules.findByTenantIdAndId(TENANT_ID, rule.id())).thenReturn(Optional.of(rule));
    assertThatThrownBy(() -> service.get(ACTOR_ID, "tenant", CALENDAR_ID, rule.id()))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("availability_rule_not_found");
  }

  @Test
  void get_whenRuleExistsOnlyInAnotherTenant_shouldReturnForbidden() {
    AvailabilityRule otherTenantRule =
        AvailabilityRule.create(
            UUID.randomUUID(),
            CALENDAR_ID,
            DayOfWeek.MONDAY,
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            NOW);
    when(rules.findByTenantIdAndId(TENANT_ID, otherTenantRule.id())).thenReturn(Optional.empty());
    when(rules.findById(otherTenantRule.id())).thenReturn(Optional.of(otherTenantRule));

    assertThatThrownBy(() -> service.get(ACTOR_ID, "tenant", CALENDAR_ID, otherTenantRule.id()))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("availability_access_denied");
  }

  @Test
  void list_shouldUseTenantAndCalendarScopedLookup() {
    when(rules.findByTenantIdAndCalendarId(TENANT_ID, CALENDAR_ID)).thenReturn(List.of(rule()));

    assertThat(service.list(ACTOR_ID, "tenant", CALENDAR_ID)).hasSize(1);
    verify(rules).findByTenantIdAndCalendarId(TENANT_ID, CALENDAR_ID);
  }

  private static AvailabilityRule rule() {
    return AvailabilityRule.create(
        TENANT_ID, CALENDAR_ID, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0), NOW);
  }
}
