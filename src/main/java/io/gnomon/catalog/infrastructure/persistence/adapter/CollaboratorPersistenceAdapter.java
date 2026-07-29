package io.gnomon.catalog.infrastructure.persistence.adapter;

import io.gnomon.catalog.application.port.out.CollaboratorRepository;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Collaborator;
import io.gnomon.catalog.infrastructure.persistence.entity.CollaboratorJpaEntity;
import io.gnomon.catalog.infrastructure.persistence.repository.SpringDataCollaboratorRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CollaboratorPersistenceAdapter implements CollaboratorRepository {

  private final SpringDataCollaboratorRepository repository;

  CollaboratorPersistenceAdapter(SpringDataCollaboratorRepository repository) {
    this.repository = repository;
  }

  @Override
  public Collaborator save(Collaborator collaborator) {
    try {
      return repository.saveAndFlush(CollaboratorJpaEntity.from(collaborator)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (contains(exception, "uq_collaborators_tenant_user")) {
        throw new CatalogException(
            "collaborator_already_linked", "user is already linked to a collaborator");
      }
      throw exception;
    }
  }

  @Override
  public Optional<Collaborator> findById(UUID id) {
    return repository.findById(id).map(CollaboratorJpaEntity::toDomain);
  }

  @Override
  public Optional<Collaborator> findByTenantIdAndId(UUID tenantId, UUID id) {
    return repository.findByTenantIdAndId(tenantId, id).map(CollaboratorJpaEntity::toDomain);
  }

  @Override
  public List<Collaborator> findByTenantId(UUID tenantId) {
    return repository.findByTenantIdOrderByDisplayNameAscIdAsc(tenantId).stream()
        .map(CollaboratorJpaEntity::toDomain)
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
