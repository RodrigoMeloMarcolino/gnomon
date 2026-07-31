package io.gnomon.availability.infrastructure.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.catalog.application.port.in.CalendarUseCase;
import io.gnomon.catalog.application.port.in.WritableCalendar;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityCalendarAccessAdapterTest {

  @Mock private CalendarUseCase calendars;

  @Test
  void requireWritableCalendar_whenOwnerTargetsTenantCalendar_shouldAllow() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID calendar = UUID.randomUUID();
    when(calendars.requireWritableCalendar(actor, "tenant", calendar))
        .thenReturn(
            new WritableCalendar(tenant, calendar, java.time.ZoneId.of("America/Fortaleza")));

    var result = adapter().requireWritableCalendar(actor, "tenant", calendar);

    assertThat(result.tenantId()).isEqualTo(tenant);
    assertThat(result.zoneId().getId()).isEqualTo("America/Fortaleza");
  }

  @Test
  void requireWritableCalendar_whenStaffOwnsCalendar_shouldAllow() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID calendar = UUID.randomUUID();
    when(calendars.requireWritableCalendar(actor, "tenant", calendar))
        .thenReturn(
            new WritableCalendar(tenant, calendar, java.time.ZoneId.of("America/Fortaleza")));

    assertThat(adapter().requireWritableCalendar(actor, "tenant", calendar).calendarId())
        .isEqualTo(calendar);
  }

  @Test
  void requireWritableCalendar_whenStaffTargetsAnotherCalendar_shouldReject() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID calendar = UUID.randomUUID();
    when(calendars.requireWritableCalendar(actor, "tenant", calendar))
        .thenThrow(
            new io.gnomon.catalog.domain.exception.CatalogException(
                "staff_calendar_mismatch", "staff can only access their own calendar"));

    assertThatThrownBy(() -> adapter().requireWritableCalendar(actor, "tenant", calendar))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("staff_calendar_mismatch");
  }

  @Test
  void requireWritableCalendar_whenCalendarExistsInAnotherTenant_shouldRejectAsForbidden() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    when(calendars.requireWritableCalendar(actor, "tenant", other))
        .thenThrow(
            new io.gnomon.catalog.domain.exception.CatalogException(
                "availability_access_denied", "cross-tenant access is forbidden"));

    assertThatThrownBy(() -> adapter().requireWritableCalendar(actor, "tenant", other))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("availability_access_denied");
  }

  private AvailabilityCalendarAccessAdapter adapter() {
    return new AvailabilityCalendarAccessAdapter(calendars);
  }
}
