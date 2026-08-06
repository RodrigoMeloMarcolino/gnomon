package io.gnomon.catalog.application.service;

import io.gnomon.catalog.application.port.in.StaffCalendarAccessUseCase;
import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StaffCalendarAccessService implements StaffCalendarAccessUseCase {
  private final CollaboratorRepository collaborators;
  private final CalendarRepository calendars;

  public StaffCalendarAccessService(
      CollaboratorRepository collaborators, CalendarRepository calendars) {
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
