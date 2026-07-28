package io.gnomon.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.catalog.application.CreateOfferingUseCase.CreateOfferingCommand;
import io.gnomon.catalog.application.ReplaceCalendarOfferingsUseCase.ReplaceCalendarOfferingsCommand;
import io.gnomon.catalog.application.port.CalendarOfferingRepository;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort.TenantAccess;
import io.gnomon.catalog.application.port.OfferingRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Offering;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfferingServiceTest {

  private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CatalogTenantAccessPort access;
  @Mock private CalendarRepository calendars;
  @Mock private OfferingRepository offerings;
  @Mock private CalendarOfferingRepository assignments;

  private OfferingService service;

  @BeforeEach
  void setUp() {
    service =
        new OfferingService(
            access, calendars, offerings, assignments, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void create_whenActorIsManager_shouldPersistActiveOffering() {
    when(access.requireManager(ACTOR_ID, "barbearia-solar")).thenReturn(tenantAccess());
    when(offerings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    OfferingResult result =
        service.create(
            new CreateOfferingCommand(ACTOR_ID, "barbearia-solar", "Corte", null, 30, 4_500));

    assertThat(result.tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.active()).isTrue();
    verify(offerings).save(any(Offering.class));
  }

  @Test
  void create_whenActiveTitleAlreadyExists_shouldReturnValidationError() {
    when(access.requireManager(ACTOR_ID, "barbearia-solar")).thenReturn(tenantAccess());
    when(offerings.activeTitleExists(any(), any(), any())).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateOfferingCommand(
                        ACTOR_ID, "barbearia-solar", "Corte", null, 30, null)))
        .isInstanceOf(CatalogException.class)
        .extracting(error -> ((CatalogException) error).code())
        .isEqualTo("validation_error");
    verify(offerings, never()).save(any());
  }

  @Test
  void get_whenOfferingBelongsToAnotherTenant_shouldDenyCrossTenantAccess() {
    UUID offeringId = UUID.randomUUID();
    Offering foreign = Offering.create(OTHER_TENANT_ID, "Corte", null, 30, null, NOW);
    when(access.requireManager(ACTOR_ID, "barbearia-solar")).thenReturn(tenantAccess());
    when(offerings.findByTenantIdAndId(TENANT_ID, offeringId)).thenReturn(Optional.empty());
    when(offerings.findById(offeringId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.get(ACTOR_ID, "barbearia-solar", offeringId))
        .isInstanceOf(CatalogException.class)
        .extracting(error -> ((CatalogException) error).code())
        .isEqualTo("catalog_access_denied");
  }

  @Test
  void replace_whenCalendarAndOfferingsBelongToTenant_shouldReplaceAtomically() {
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    Calendar calendar = org.mockito.Mockito.mock(Calendar.class);
    Offering first = offering(firstId, "Corte");
    Offering second = offering(secondId, "Barba");
    when(access.requireManager(ACTOR_ID, "barbearia-solar")).thenReturn(tenantAccess());
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar));
    when(offerings.findByTenantIdAndId(TENANT_ID, firstId)).thenReturn(Optional.of(first));
    when(offerings.findByTenantIdAndId(TENANT_ID, secondId)).thenReturn(Optional.of(second));

    List<OfferingResult> result =
        service.replace(
            new ReplaceCalendarOfferingsCommand(
                ACTOR_ID, "barbearia-solar", CALENDAR_ID, Set.of(firstId, secondId)));

    assertThat(result).extracting(OfferingResult::title).containsExactly("Barba", "Corte");
    verify(assignments).replace(TENANT_ID, CALENDAR_ID, Set.of(firstId, secondId));
  }

  @Test
  void replace_whenOfferingIdsIsNull_shouldReturnValidationError() {
    assertThatThrownBy(
            () ->
                service.replace(
                    new ReplaceCalendarOfferingsCommand(
                        ACTOR_ID, "barbearia-solar", CALENDAR_ID, null)))
        .isInstanceOf(CatalogException.class)
        .extracting(error -> ((CatalogException) error).code())
        .isEqualTo("validation_error");
    verify(access, never()).requireManager(any(), any());
    verify(assignments, never()).replace(any(), any(), any());
  }

  @Test
  void listPublic_whenCalendarIsInactive_shouldHideCalendar() {
    Calendar calendar = org.mockito.Mockito.mock(Calendar.class);
    when(calendar.active()).thenReturn(false);
    when(access.requirePublicTenant("barbearia-solar")).thenReturn(tenantAccess());
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID)).thenReturn(Optional.of(calendar));

    assertThatThrownBy(() -> service.list("barbearia-solar", CALENDAR_ID))
        .isInstanceOf(CatalogException.class)
        .extracting(error -> ((CatalogException) error).code())
        .isEqualTo("calendar_not_found");
    verify(offerings, never()).findActiveByTenantId(any(), any());
  }

  private static TenantAccess tenantAccess() {
    return new TenantAccess(
        TENANT_ID, "Barbearia Solar", "barbearia-solar", "America/Fortaleza", "BRL", "owner");
  }

  private static Offering offering(UUID id, String title) {
    return new Offering(id, TENANT_ID, title, null, 30, null, true, NOW, NOW);
  }
}
