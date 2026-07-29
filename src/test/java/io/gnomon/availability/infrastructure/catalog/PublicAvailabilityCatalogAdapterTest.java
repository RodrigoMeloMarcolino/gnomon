package io.gnomon.availability.infrastructure.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.gnomon.catalog.application.port.in.SchedulableOffering;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase;
import io.gnomon.catalog.domain.exception.CatalogException;
import java.time.ZoneId;
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
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

  @Mock private SchedulableOfferingUseCase catalog;

  private PublicAvailabilityCatalogAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PublicAvailabilityCatalogAdapter(catalog);
  }

  @Test
  void requireSchedulableOffering_whenAllResourcesAreActiveAndAssigned_shouldReturnContext() {
    when(catalog.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .thenReturn(schedulableOffering());

    var result = adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID);

    assertThat(result.tenantId()).isEqualTo(TENANT_ID);
    assertThat(result.calendarId()).isEqualTo(CALENDAR_ID);
    assertThat(result.zoneId()).isEqualTo(ZoneId.of("America/Fortaleza"));
    assertThat(result.durationMinutes()).isEqualTo(30);
  }

  @Test
  void requireSchedulableOffering_whenCalendarIsInactive_shouldReturnCanonicalNotFound() {
    when(catalog.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .thenThrow(new CatalogException("calendar_not_found", "calendar was not found"));

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("calendar_not_found");
  }

  @Test
  void
      requireSchedulableOffering_whenOfferingIsInactiveOrUnassigned_shouldReturnCanonicalNotFound() {
    when(catalog.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .thenThrow(new CatalogException("offering_not_found", "offering was not found"));

    assertThatThrownBy(
            () -> adapter.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("offering_not_found");
  }

  private static SchedulableOffering schedulableOffering() {
    return new SchedulableOffering(
        TENANT_ID,
        CALENDAR_ID,
        "Agenda",
        ZoneId.of("America/Fortaleza"),
        OFFERING_ID,
        "Corte",
        30,
        4_500);
  }
}
