package io.gnomon.catalog.infrastructure.persistence.repository;

import io.gnomon.catalog.infrastructure.persistence.entity.CollaboratorJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCollaboratorRepository
    extends JpaRepository<CollaboratorJpaEntity, UUID> {

  Optional<CollaboratorJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<CollaboratorJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

  List<CollaboratorJpaEntity> findByTenantIdOrderByDisplayNameAscIdAsc(UUID tenantId);
}
