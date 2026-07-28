package io.gnomon.booking.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.gnomon.booking.domain.BookingException;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort.TenantAccess;
import io.gnomon.catalog.application.port.OfferingRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Offering;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingCatalogAdapterTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID COLLABORATOR_ID =
      UUID.fromString("35000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CatalogTenantAccessPort tenantAccess;
  @Mock private CalendarRepository calendars;
  @Mock private OfferingRepository offerings;

  private BookingCatalogAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new BookingCatalogAdapter(tenantAccess, calendars, offerings);
  }

  @Test
  void requireSchedulableOffering_whenResourcesAreActiveAndAssigned_shouldReturnFullSnapshot() {
    givenTenant();
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(true)));
    when(offerings.findActiveByTenantId(TENANT_ID, CALENDAR_ID)).thenReturn(List.of(offering()));

    var result = adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID);

    assertThat(result.tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.calendarName()).isEqualTo("Agenda da Joana");
    assertThat(result.zoneId()).isEqualTo(ZoneId.of("America/Fortaleza"));
    assertThat(result.offeringTitle()).isEqualTo("Corte");
    assertThat(result.durationMinutes()).isEqualTo(30);
    assertThat(result.priceCents()).isEqualTo(4_500);
  }

  @Test
  void requireSchedulableOffering_whenCalendarIsInactive_shouldHideItAsNotFound() {
    givenTenant();
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(false)));

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("calendar_not_found");
  }

  @Test
  void requireSchedulableOffering_whenOfferingIsNotAssigned_shouldHideItAsNotFound() {
    givenTenant();
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(true)));
    when(offerings.findActiveByTenantId(TENANT_ID, CALENDAR_ID)).thenReturn(List.of());

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("offering_not_found");
  }

  @Test
  void requireSchedulableOffering_whenTenantIsMissing_shouldTranslateCatalogError() {
    when(tenantAccess.requirePublicTenant("missing"))
        .thenThrow(new CatalogException("tenant_not_found", "tenant was not found"));

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("missing", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("tenant_not_found");
  }

  private void givenTenant() {
    when(tenantAccess.requirePublicTenant("barbearia-solar"))
        .thenReturn(
            new TenantAccess(
                TENANT_ID, "Barbearia Solar", "barbearia-solar", "America/Fortaleza", "BRL", null));
  }

  private static Calendar calendar(boolean active) {
    return new Calendar(
        CALENDAR_ID,
        TENANT_ID,
        COLLABORATOR_ID,
        "Agenda da Joana",
        "America/Fortaleza",
        active,
        NOW,
        NOW);
  }

  private static Offering offering() {
    return new Offering(OFFERING_ID, TENANT_ID, "Corte", null, 30, 4_500, true, NOW, NOW);
  }
}
