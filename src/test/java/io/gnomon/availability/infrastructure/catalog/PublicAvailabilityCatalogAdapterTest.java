package io.gnomon.availability.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

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
class PublicAvailabilityCatalogAdapterTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID COLLABORATOR_ID =
      UUID.fromString("35000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CatalogTenantAccessPort tenantAccess;
  @Mock private CalendarRepository calendars;
  @Mock private OfferingRepository offerings;

  private PublicAvailabilityCatalogAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PublicAvailabilityCatalogAdapter(tenantAccess, calendars, offerings);
    when(tenantAccess.requirePublicTenant("barbearia-solar"))
        .thenReturn(
            new TenantAccess(
                TENANT_ID, "Barbearia Solar", "barbearia-solar", "America/Fortaleza", "BRL", null));
  }

  @Test
  void requireSchedulableOffering_whenAllResourcesAreActiveAndAssigned_shouldReturnContext() {
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(true)));
    when(offerings.findActiveByTenantId(TENANT_ID, CALENDAR_ID))
        .thenReturn(List.of(offering(true)));

    var result = adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID);

    assertThat(result.tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.calendarId()).isEqualTo(CALENDAR_ID);
    assertThat(result.zoneId()).isEqualTo(ZoneId.of("America/Fortaleza"));
    assertThat(result.durationMinutes()).isEqualTo(30);
  }

  @Test
  void requireSchedulableOffering_whenCalendarIsInactive_shouldReturnCanonicalNotFound() {
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(false)));

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("calendar_not_found");
  }

  @Test
  void
      requireSchedulableOffering_whenOfferingIsInactiveOrUnassigned_shouldReturnCanonicalNotFound() {
    when(calendars.findByTenantIdAndId(TENANT_ID, CALENDAR_ID))
        .thenReturn(Optional.of(calendar(true)));
    when(offerings.findActiveByTenantId(TENANT_ID, CALENDAR_ID)).thenReturn(List.of());

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("offering_not_found");
  }

  private static Calendar calendar(boolean active) {
    return new Calendar(
        CALENDAR_ID, TENANT_ID, COLLABORATOR_ID, "Agenda", "America/Fortaleza", active, NOW, NOW);
  }

  private static Offering offering(boolean active) {
    return new Offering(OFFERING_ID, TENANT_ID, "Corte", null, 30, 4_500, active, NOW, NOW);
  }
}
