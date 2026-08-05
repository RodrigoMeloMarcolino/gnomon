package io.gnomon.catalog.infrastructure.integration.booking;

import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import io.gnomon.catalog.application.port.out.StaffCalendarAccessPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class StaffCalendarAccessAdapter implements StaffCalendarAccessPort {
  private final CollaboratorRepository collaborators;
  private final CalendarRepository calendars;

  StaffCalendarAccessAdapter(CollaboratorRepository collaborators, CalendarRepository calendars) {
    this.collaborators = collaborators;
    this.calendars = calendars;
  }

  @Override
  public Optional<UUID> findCalendarIdForStaff(UUID tenantId, UUID userId) {
    return collaborators
        .findByTenantIdAndUserId(tenantId, userId)
        .flatMap(value -> calendars.findByTenantIdAndCollaboratorId(tenantId, value.id()))
        .map(io.gnomon.catalog.domain.model.Calendar::id);
  }
}
