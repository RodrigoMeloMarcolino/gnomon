package io.gnomon.catalog.infrastructure.persistence;

import io.gnomon.catalog.application.port.OfferingRepository;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Offering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class OfferingPersistenceAdapter implements OfferingRepository {

  private final SpringDataOfferingRepository repository;

  OfferingPersistenceAdapter(SpringDataOfferingRepository repository) {
    this.repository = repository;
  }

  @Override
  public Offering save(Offering offering) {
    try {
      return repository.saveAndFlush(OfferingJpaEntity.from(offering)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (containsConstraint(exception, "uq_offerings_active_tenant_title")) {
        throw new CatalogException(
            "validation_error", "an active offering already uses this title");
      }
      if (containsConstraint(exception, "ck_offerings_title_not_blank")
          || containsConstraint(exception, "ck_offerings_duration")
          || containsConstraint(exception, "ck_offerings_price")) {
        throw new CatalogException("validation_error", "offering data violates a constraint");
      }
      throw exception;
    }
  }

  @Override
  public Optional<Offering> findById(UUID id) {
    return repository.findById(id).map(OfferingJpaEntity::toDomain);
  }

  @Override
  public Optional<Offering> findByTenantIdAndId(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id).map(OfferingJpaEntity::toDomain);
  }

  @Override
  public List<Offering> findByTenantId(UUID tenantId) {
    return repository.findByTenantIdOrderByTitleAsc(tenantId).stream()
        .map(OfferingJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<Offering> findActiveByTenantId(UUID tenantId, UUID calendarId) {
    List<OfferingJpaEntity> entities =
        calendarId == null
            ? repository.findByTenantIdAndActiveTrueOrderByTitleAsc(tenantId)
            : repository.findActiveAssignedToCalendar(tenantId, calendarId);
    return entities.stream().map(OfferingJpaEntity::toDomain).toList();
  }

  @Override
  public boolean activeTitleExists(UUID tenantId, String normalizedTitle, UUID excludedOfferingId) {
    return repository.activeTitleExists(tenantId, normalizedTitle, excludedOfferingId);
  }

  private static boolean containsConstraint(Throwable exception, String constraint) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
