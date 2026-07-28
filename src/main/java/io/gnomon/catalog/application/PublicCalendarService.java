package io.gnomon.catalog.application;

import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PublicCalendarService implements PublicCalendarUseCase {

  private final CalendarRepository calendars;
  private final CollaboratorRepository collaborators;
  private final CatalogTenantAccessPort tenantAccess;

  public PublicCalendarService(
      CalendarRepository calendars,
      CollaboratorRepository collaborators,
      CatalogTenantAccessPort tenantAccess) {
    this.calendars = calendars;
    this.collaborators = collaborators;
    this.tenantAccess = tenantAccess;
  }

  @Override
  public java.util.List<PublicCalendarResult> listActive(String tenantSlug) {
    var access = tenantAccess.requirePublicTenant(tenantSlug);
    Map<java.util.UUID, io.gnomon.catalog.domain.Collaborator> collaboratorById =
        collaborators.findByTenantId(access.tenantId()).stream()
            .filter(io.gnomon.catalog.domain.Collaborator::active)
            .collect(
                Collectors.toMap(io.gnomon.catalog.domain.Collaborator::id, Function.identity()));
    return calendars.findActiveByTenantId(access.tenantId()).stream()
        .filter(calendar -> collaboratorById.containsKey(calendar.collaboratorId()))
        .map(
            calendar ->
                new PublicCalendarResult(
                    calendar.id(),
                    calendar.collaboratorId(),
                    collaboratorById.get(calendar.collaboratorId()).displayName(),
                    calendar.name(),
                    calendar.timezone()))
        .toList();
  }
}
