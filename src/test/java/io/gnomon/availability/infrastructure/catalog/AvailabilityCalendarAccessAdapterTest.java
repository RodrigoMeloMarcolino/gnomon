package io.gnomon.availability.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.gnomon.availability.domain.AvailabilityException;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort.TenantAccess;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.Collaborator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityCalendarAccessAdapterTest {

  private static final Instant NOW = Instant.parse("2027-07-01T12:00:00Z");

  @Mock private CatalogTenantAccessPort tenantAccess;
  @Mock private CalendarRepository calendars;
  @Mock private CollaboratorRepository collaborators;

  @Test
  void requireWritableCalendar_whenOwnerTargetsTenantCalendar_shouldAllow() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    Calendar calendar =
        Calendar.create(tenant, UUID.randomUUID(), "Agenda", "America/Fortaleza", NOW);
    when(tenantAccess.requireMember(actor, "tenant")).thenReturn(access(tenant, "owner"));
    when(calendars.findByTenantIdAndId(tenant, calendar.id())).thenReturn(Optional.of(calendar));

    var result = adapter().requireWritableCalendar(actor, "tenant", calendar.id());

    assertThat(result.tenantId()).isEqualTo(tenant);
    assertThat(result.zoneId().getId()).isEqualTo("America/Fortaleza");
  }

  @Test
  void requireWritableCalendar_whenStaffOwnsCalendar_shouldAllow() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID collaboratorId = UUID.randomUUID();
    Calendar calendar = Calendar.create(tenant, collaboratorId, "Agenda", "America/Fortaleza", NOW);
    Collaborator collaborator =
        new Collaborator(collaboratorId, tenant, actor, "Staff", true, NOW, NOW);
    when(tenantAccess.requireMember(actor, "tenant")).thenReturn(access(tenant, "staff"));
    when(calendars.findByTenantIdAndId(tenant, calendar.id())).thenReturn(Optional.of(calendar));
    when(collaborators.findByTenantIdAndId(tenant, collaboratorId))
        .thenReturn(Optional.of(collaborator));

    assertThat(adapter().requireWritableCalendar(actor, "tenant", calendar.id()).calendarId())
        .isEqualTo(calendar.id());
  }

  @Test
  void requireWritableCalendar_whenStaffTargetsAnotherCalendar_shouldReject() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID collaboratorId = UUID.randomUUID();
    Calendar calendar = Calendar.create(tenant, collaboratorId, "Agenda", "America/Fortaleza", NOW);
    Collaborator collaborator =
        new Collaborator(collaboratorId, tenant, UUID.randomUUID(), "Other", true, NOW, NOW);
    when(tenantAccess.requireMember(actor, "tenant")).thenReturn(access(tenant, "staff"));
    when(calendars.findByTenantIdAndId(tenant, calendar.id())).thenReturn(Optional.of(calendar));
    when(collaborators.findByTenantIdAndId(tenant, collaboratorId))
        .thenReturn(Optional.of(collaborator));

    assertThatThrownBy(() -> adapter().requireWritableCalendar(actor, "tenant", calendar.id()))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("staff_calendar_mismatch");
  }

  @Test
  void requireWritableCalendar_whenCalendarExistsInAnotherTenant_shouldRejectAsForbidden() {
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    Calendar other =
        Calendar.create(UUID.randomUUID(), UUID.randomUUID(), "Other", "America/Fortaleza", NOW);
    when(tenantAccess.requireMember(actor, "tenant")).thenReturn(access(tenant, "admin"));
    when(calendars.findByTenantIdAndId(tenant, other.id())).thenReturn(Optional.empty());
    when(calendars.findById(other.id())).thenReturn(Optional.of(other));

    assertThatThrownBy(() -> adapter().requireWritableCalendar(actor, "tenant", other.id()))
        .isInstanceOf(AvailabilityException.class)
        .extracting(exception -> ((AvailabilityException) exception).code())
        .isEqualTo("availability_access_denied");
  }

  private AvailabilityCalendarAccessAdapter adapter() {
    return new AvailabilityCalendarAccessAdapter(tenantAccess, calendars, collaborators);
  }

  private static TenantAccess access(UUID tenantId, String role) {
    return new TenantAccess(tenantId, "Tenant", "tenant", "America/Fortaleza", "BRL", role);
  }
}
