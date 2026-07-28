package io.gnomon.availability.infrastructure.catalog;

import io.gnomon.availability.application.port.AvailabilityCalendarAccessPort;
import io.gnomon.availability.domain.AvailabilityException;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import io.gnomon.catalog.domain.Calendar;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AvailabilityCalendarAccessAdapter implements AvailabilityCalendarAccessPort {

  private final CatalogTenantAccessPort tenantAccess;
  private final CalendarRepository calendars;
  private final CollaboratorRepository collaborators;

  AvailabilityCalendarAccessAdapter(
      CatalogTenantAccessPort tenantAccess,
      CalendarRepository calendars,
      CollaboratorRepository collaborators) {
    this.tenantAccess = tenantAccess;
    this.calendars = calendars;
    this.collaborators = collaborators;
  }

  @Override
  public CalendarContext requireWritableCalendar(
      UUID actorUserId, String tenantSlug, UUID calendarId) {
    var access = tenantAccess.requireMember(actorUserId, tenantSlug);
    Calendar calendar =
        calendars
            .findByTenantIdAndId(access.tenantId(), calendarId)
            .orElseGet(
                () -> {
                  if (calendars.findById(calendarId).isPresent()) {
                    throw new AvailabilityException(
                        "availability_access_denied", "cross-tenant access is forbidden");
                  }
                  throw new AvailabilityException("calendar_not_found", "calendar was not found");
                });
    if ("staff".equals(access.actorRole())) {
      var collaborator =
          collaborators
              .findByTenantIdAndId(access.tenantId(), calendar.collaboratorId())
              .orElseThrow(
                  () -> new AvailabilityException("calendar_not_found", "calendar was not found"));
      if (!actorUserId.equals(collaborator.userId())) {
        throw new AvailabilityException(
            "staff_calendar_mismatch", "staff can only access their own calendar");
      }
    }
    return new CalendarContext(access.tenantId(), calendar.id(), ZoneId.of(calendar.timezone()));
  }
}
