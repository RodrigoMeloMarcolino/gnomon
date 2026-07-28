package io.gnomon.catalog.application;

import io.gnomon.catalog.application.CollaboratorUseCase.CreateCollaboratorCommand;
import io.gnomon.catalog.application.CollaboratorUseCase.LinkCollaboratorUserCommand;
import io.gnomon.catalog.application.CollaboratorUseCase.UpdateCollaboratorCommand;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Collaborator;
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

  @Autowired
  public CollaboratorService(
      CollaboratorRepository collaborators,
      CalendarRepository calendars,
      CatalogTenantAccessPort tenantAccess) {
    this(collaborators, calendars, tenantAccess, Clock.systemUTC());
  }

  CollaboratorService(
      CollaboratorRepository collaborators,
      CalendarRepository calendars,
      CatalogTenantAccessPort tenantAccess,
      Clock clock) {
    this.collaborators = collaborators;
    this.calendars = calendars;
    this.tenantAccess = tenantAccess;
    this.clock = clock;
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
    return result(collaborators.save(collaborator));
  }

  @Override
  @Transactional
  public void deactivate(UUID actorUserId, String tenantSlug, UUID collaboratorId) {
    var access = tenantAccess.requireManager(actorUserId, tenantSlug);
    Collaborator collaborator = requireInTenant(access.tenantId(), collaboratorId);
    var now = clock.instant();
    collaborator.deactivate(now);
    Calendar calendar = requireCalendar(collaborator);
    calendar.deactivate(now);
    collaborators.save(collaborator);
    calendars.save(calendar);
  }

  @Override
  @Transactional
  public CollaboratorResult linkUser(LinkCollaboratorUserCommand command) {
    var access = tenantAccess.requireManager(command.actorUserId(), command.tenantSlug());
    Collaborator collaborator = requireInTenant(access.tenantId(), command.collaboratorId());
    var link = tenantAccess.linkStaff(access.tenantId(), command.userEmail(), clock.instant());
    collaborator.link(link.userId(), clock.instant());
    return result(collaborators.save(collaborator));
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
    return result(collaborators.save(collaborator));
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
