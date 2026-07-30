package io.gnomon.catalog.application.service;

import io.gnomon.catalog.application.port.in.CollaboratorUseCase;
import io.gnomon.catalog.application.port.in.CreateCollaboratorCommand;
import io.gnomon.catalog.application.port.in.LinkCollaboratorUserCommand;
import io.gnomon.catalog.application.port.in.UpdateCollaboratorCommand;
import io.gnomon.catalog.application.port.in.result.CalendarResult;
import io.gnomon.catalog.application.port.in.result.CollaboratorResult;
import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CatalogAvailabilityCachePort;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import io.gnomon.catalog.application.port.out.PublicCatalogCachePort;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Calendar;
import io.gnomon.catalog.domain.model.Collaborator;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CollaboratorService implements CollaboratorUseCase {

  private final CollaboratorRepository collaborators;
  private final CalendarRepository calendars;
  private final CatalogTenantAccessPort tenantAccess;
  private final Clock clock;
  private final PublicCatalogCachePort cache;
  private final CatalogAvailabilityCachePort availabilityCache;

  public CollaboratorService(
      CollaboratorRepository collaborators,
      CalendarRepository calendars,
      CatalogTenantAccessPort tenantAccess,
      Clock clock,
      PublicCatalogCachePort cache) {
    this(collaborators, calendars, tenantAccess, clock, cache, (tenantId, calendarId) -> {});
  }

  @Autowired
  public CollaboratorService(
      CollaboratorRepository collaborators,
      CalendarRepository calendars,
      CatalogTenantAccessPort tenantAccess,
      PublicCatalogCachePort cache,
      CatalogAvailabilityCachePort availabilityCache) {
    this(collaborators, calendars, tenantAccess, Clock.systemUTC(), cache, availabilityCache);
  }

  public CollaboratorService(
      CollaboratorRepository collaborators,
      CalendarRepository calendars,
      CatalogTenantAccessPort tenantAccess,
      Clock clock,
      PublicCatalogCachePort cache,
      CatalogAvailabilityCachePort availabilityCache) {
    this.collaborators = collaborators;
    this.calendars = calendars;
    this.tenantAccess = tenantAccess;
    this.clock = clock;
    this.cache = cache;
    this.availabilityCache = availabilityCache;
  }

  @Override
  @Transactional
  public CollaboratorResult create(CreateCollaboratorCommand command) {
    var access = tenantAccess.requireManager(command.actorUserId(), command.tenantSlug());
    var now = clock.instant();
    Collaborator collaborator =
        collaborators.save(Collaborator.create(access.tenantId(), command.displayName(), now));
    Calendar calendar =
        calendars.save(
            Calendar.create(
                access.tenantId(),
                collaborator.id(),
                collaborator.displayName(),
                access.defaultTimezone(),
                now));
    cache.invalidateAfterCommit(access.tenantId());
    availabilityCache.invalidateCalendarAfterCommit(access.tenantId(), calendar.id());
    return CollaboratorResult.from(collaborator, CalendarResult.from(calendar));
  }

  @Override
  public List<CollaboratorResult> list(UUID actorUserId, String tenantSlug) {
    var access = tenantAccess.requireManager(actorUserId, tenantSlug);
    Map<UUID, Calendar> byCollaborator =
        calendars.findByTenantId(access.tenantId()).stream()
            .collect(Collectors.toMap(Calendar::collaboratorId, Function.identity()));
    return collaborators.findByTenantId(access.tenantId()).stream()
        .map(
            collaborator ->
                CollaboratorResult.from(
                    collaborator, CalendarResult.from(byCollaborator.get(collaborator.id()))))
        .toList();
  }

  @Override
  public CollaboratorResult get(UUID actorUserId, String tenantSlug, UUID collaboratorId) {
    var access = tenantAccess.requireManager(actorUserId, tenantSlug);
    Collaborator collaborator = requireInTenant(access.tenantId(), collaboratorId);
    return result(collaborator);
  }

  @Override
  @Transactional
  public CollaboratorResult update(UpdateCollaboratorCommand command) {
    var access = tenantAccess.requireManager(command.actorUserId(), command.tenantSlug());
    Collaborator collaborator = requireInTenant(access.tenantId(), command.collaboratorId());
    collaborator.rename(command.displayName(), clock.instant());
    CollaboratorResult result = result(collaborators.save(collaborator));
    cache.invalidateAfterCommit(access.tenantId());
    return result;
  }

  @Override
  @Transactional
  public void deactivate(UUID actorUserId, String tenantSlug, UUID collaboratorId) {
    var access = tenantAccess.requireManager(actorUserId, tenantSlug);
    Collaborator collaborator = requireInTenant(access.tenantId(), collaboratorId);
    var now = clock.instant();
    UUID previousUserId = collaborator.unlink(now);
    if (previousUserId != null) {
      tenantAccess.unlinkStaff(access.tenantId(), previousUserId);
    }
    collaborator.deactivate(now);
    Calendar calendar = requireCalendar(collaborator);
    calendar.deactivate(now);
    collaborators.save(collaborator);
    calendars.save(calendar);
    cache.invalidateAfterCommit(access.tenantId());
  }

  @Override
  @Transactional
  public CollaboratorResult linkUser(LinkCollaboratorUserCommand command) {
    var access = tenantAccess.requireManager(command.actorUserId(), command.tenantSlug());
    Collaborator collaborator = requireInTenant(access.tenantId(), command.collaboratorId());
    var link = tenantAccess.linkStaff(access.tenantId(), command.userEmail(), clock.instant());
    collaborator.link(link.userId(), clock.instant());
    CollaboratorResult result = result(collaborators.save(collaborator));
    cache.invalidateAfterCommit(access.tenantId());
    return result;
  }

  @Override
  @Transactional
  public CollaboratorResult unlinkUser(UUID actorUserId, String tenantSlug, UUID collaboratorId) {
    var access = tenantAccess.requireManager(actorUserId, tenantSlug);
    Collaborator collaborator = requireInTenant(access.tenantId(), collaboratorId);
    UUID previousUserId = collaborator.unlink(clock.instant());
    if (previousUserId != null) {
      tenantAccess.unlinkStaff(access.tenantId(), previousUserId);
    }
    CollaboratorResult result = result(collaborators.save(collaborator));
    cache.invalidateAfterCommit(access.tenantId());
    return result;
  }

  private CollaboratorResult result(Collaborator collaborator) {
    return CollaboratorResult.from(
        collaborator, CalendarResult.from(requireCalendar(collaborator)));
  }

  private Calendar requireCalendar(Collaborator collaborator) {
    return calendars
        .findByTenantIdAndCollaboratorId(collaborator.tenantId(), collaborator.id())
        .orElseThrow(() -> new CatalogException("calendar_not_found", "calendar was not found"));
  }

  private Collaborator requireInTenant(UUID tenantId, UUID id) {
    return collaborators
        .findByTenantIdAndId(tenantId, id)
        .orElseGet(
            () -> {
              if (collaborators.findById(id).isPresent()) {
                throw new CatalogException(
                    "catalog_access_denied", "cross-tenant access is forbidden");
              }
              throw new CatalogException("collaborator_not_found", "collaborator was not found");
            });
  }
}
