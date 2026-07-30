package io.gnomon.catalog.application.service;

import io.gnomon.catalog.application.port.in.PublicCalendarUseCase;
import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import io.gnomon.catalog.application.port.out.PublicCatalogCachePort;
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
  private final PublicCatalogCachePort cache;

  public PublicCalendarService(
      CalendarRepository calendars,
      CollaboratorRepository collaborators,
      CatalogTenantAccessPort tenantAccess,
      PublicCatalogCachePort cache) {
    this.calendars = calendars;
    this.collaborators = collaborators;
    this.tenantAccess = tenantAccess;
    this.cache = cache;
  }

  @Override
  public java.util.List<PublicCalendarResult> listActive(String tenantSlug) {
    var access = tenantAccess.requirePublicTenant(tenantSlug);
    return cache.calendars(access.tenantId(), () -> listActiveFromDatabase(access.tenantId()));
  }

  private java.util.List<PublicCalendarResult> listActiveFromDatabase(java.util.UUID tenantId) {
    Map<java.util.UUID, io.gnomon.catalog.domain.model.Collaborator> collaboratorById =
        collaborators.findByTenantId(tenantId).stream()
            .filter(io.gnomon.catalog.domain.model.Collaborator::active)
            .collect(
                Collectors.toMap(
                    io.gnomon.catalog.domain.model.Collaborator::id, Function.identity()));
    return calendars.findActiveByTenantId(tenantId).stream()
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
