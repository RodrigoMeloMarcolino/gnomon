package io.gnomon.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import io.gnomon.catalog.application.port.out.TenantAccess;
import io.gnomon.catalog.application.service.CalendarService;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Calendar;
import io.gnomon.catalog.domain.model.Collaborator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

  @Mock private CalendarRepository calendars;
  @Mock private CollaboratorRepository collaborators;
  @Mock private CatalogTenantAccessPort tenantAccess;

  @Test
  void get_whenStaffTargetsAnotherCalendar_shouldReject() {
    Instant now = Instant.parse("2026-07-28T18:00:00Z");
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    UUID collaboratorId = UUID.randomUUID();
    Calendar calendar = Calendar.create(tenant, collaboratorId, "Agenda", "America/Fortaleza", now);
    Collaborator collaborator =
        new Collaborator(collaboratorId, tenant, UUID.randomUUID(), "Other", true, now, now);
    when(tenantAccess.requireMember(actor, "tenant"))
        .thenReturn(
            new TenantAccess(tenant, "Tenant", "tenant", "America/Fortaleza", "BRL", "staff"));
    when(calendars.findByTenantIdAndId(tenant, calendar.id())).thenReturn(Optional.of(calendar));
    when(collaborators.findByTenantIdAndId(tenant, collaboratorId))
        .thenReturn(Optional.of(collaborator));
    var service =
        new CalendarService(
            calendars, collaborators, tenantAccess, Clock.fixed(now, ZoneOffset.UTC));

    assertThatThrownBy(() -> service.get(actor, "tenant", calendar.id()))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("staff_calendar_mismatch");
  }

  @Test
  void requireWritableCalendar_whenOwnerTargetsTenantCalendar_shouldExposeAuthorizedContext() {
    Instant now = Instant.parse("2026-07-28T18:00:00Z");
    UUID actor = UUID.randomUUID();
    UUID tenant = UUID.randomUUID();
    Calendar calendar =
        Calendar.create(tenant, UUID.randomUUID(), "Agenda", "America/Fortaleza", now);
    when(tenantAccess.requireMember(actor, "tenant"))
        .thenReturn(
            new TenantAccess(tenant, "Tenant", "tenant", "America/Fortaleza", "BRL", "owner"));
    when(calendars.findByTenantIdAndId(tenant, calendar.id())).thenReturn(Optional.of(calendar));
    var service =
        new CalendarService(
            calendars, collaborators, tenantAccess, Clock.fixed(now, ZoneOffset.UTC));

    var result = service.requireWritableCalendar(actor, "tenant", calendar.id());

    assertThat(result.tenantId()).isEqualTo(tenant);
    assertThat(result.calendarId()).isEqualTo(calendar.id());
    assertThat(result.zoneId().getId()).isEqualTo("America/Fortaleza");
  }
}
