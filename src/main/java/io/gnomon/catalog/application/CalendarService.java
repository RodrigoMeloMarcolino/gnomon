package io.gnomon.catalog.application;

import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Collaborator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalendarService implements CalendarUseCase {

  private final CalendarRepository calendars;
  private final CollaboratorRepository collaborators;
  private final CatalogTenantAccessPort tenantAccess;
  private final Clock clock;

  @Autowired
  public CalendarService(
      CalendarRepository calendars,
      CollaboratorRepository collaborators,
      CatalogTenantAccessPort tenantAccess) {
    this(calendars, collaborators, tenantAccess, Clock.systemUTC());
  }

  CalendarService(
      CalendarRepository calendars,
      CollaboratorRepository collaborators,
      CatalogTenantAccessPort tenantAccess,
      Clock clock) {
    this.calendars = calendars;
    this.collaborators = collaborators;
    this.tenantAccess = tenantAccess;
    this.clock = clock;
  }

  @Override
  public CalendarResult get(UUID actorUserId, String tenantSlug, UUID calendarId) {
    var access = tenantAccess.requireMember(actorUserId, tenantSlug);
    Calendar calendar = requireInTenant(access.tenantId(), calendarId);
    requireOwnCalendar(access.actorRole(), actorUserId, calendar);
    return CalendarResult.from(calendar);
  }

  @Override
  @Transactional
  public CalendarResult update(UpdateCalendarCommand command) {
    var access = tenantAccess.requireMember(command.actorUserId(), command.tenantSlug());
    Calendar calendar = requireInTenant(access.tenantId(), command.calendarId());
    requireOwnCalendar(access.actorRole(), command.actorUserId(), calendar);
    calendar.update(command.name(), command.timezone(), command.active(), clock.instant());
    return CalendarResult.from(calendars.save(calendar));
  }

  @Override
  @Transactional
  public void deactivate(UUID actorUserId, String tenantSlug, UUID calendarId) {
    var access = tenantAccess.requireMember(actorUserId, tenantSlug);
    Calendar calendar = requireInTenant(access.tenantId(), calendarId);
    requireOwnCalendar(access.actorRole(), actorUserId, calendar);
    calendar.deactivate(clock.instant());
    calendars.save(calendar);
  }

  private void requireOwnCalendar(String role, UUID actorUserId, Calendar calendar) {
    if (!"staff".equals(role)) {
      return;
    }
    Collaborator collaborator =
        collaborators
            .findByTenantIdAndId(calendar.tenantId(), calendar.collaboratorId())
            .orElseThrow(
                () -> new CatalogException("collaborator_not_found", "collaborator was not found"));
    if (!actorUserId.equals(collaborator.userId())) {
      throw new CatalogException(
          "staff_calendar_mismatch", "staff can only access their own calendar");
    }
  }

  private Calendar requireInTenant(UUID tenantId, UUID id) {
    return calendars
        .findByTenantIdAndId(tenantId, id)
        .orElseGet(
            () -> {
              if (calendars.findById(id).isPresent()) {
                throw new CatalogException(
                    "catalog_access_denied", "cross-tenant access is forbidden");
              }
              throw new CatalogException("calendar_not_found", "calendar was not found");
            });
  }
}
