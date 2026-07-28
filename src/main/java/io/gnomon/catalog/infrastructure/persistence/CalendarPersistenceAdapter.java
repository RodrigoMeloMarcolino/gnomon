package io.gnomon.catalog.infrastructure.persistence;

import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CalendarPersistenceAdapter implements CalendarRepository {

  private final SpringDataCalendarRepository repository;

  CalendarPersistenceAdapter(SpringDataCalendarRepository repository) {
    this.repository = repository;
  }

  @Override
  public Calendar save(Calendar calendar) {
    try {
      return repository.saveAndFlush(CalendarJpaEntity.from(calendar)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (contains(exception, "uq_calendars_tenant_collaborator")) {
        throw new CatalogException("calendar_exists", "collaborator already has a calendar");
      }
      throw exception;
    }
  }

  @Override
  public Optional<Calendar> findById(UUID id) {
    return repository.findById(id).map(CalendarJpaEntity::toDomain);
  }

  @Override
  public Optional<Calendar> findByTenantIdAndId(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id).map(CalendarJpaEntity::toDomain);
  }

  @Override
  public Optional<Calendar> findByTenantIdAndCollaboratorId(UUID tenantId, UUID collaboratorId) {
    return repository
        .findByTenantIdAndCollaboratorId(tenantId, collaboratorId)
        .map(CalendarJpaEntity::toDomain);
  }

  @Override
  public List<Calendar> findByTenantId(UUID tenantId) {
    return repository.findByTenantIdOrderByNameAscIdAsc(tenantId).stream()
        .map(CalendarJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<Calendar> findActiveByTenantId(UUID tenantId) {
    return repository.findByTenantIdAndActiveTrueOrderByNameAscIdAsc(tenantId).stream()
        .map(CalendarJpaEntity::toDomain)
        .toList();
  }

  private static boolean contains(Throwable throwable, String value) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(value)) {
        return true;
      }
    }
    return false;
  }
}
