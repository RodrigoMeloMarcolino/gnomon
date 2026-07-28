package io.gnomon.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCollaboratorRepository extends JpaRepository<CollaboratorJpaEntity, UUID> {

  Optional<CollaboratorJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  List<CollaboratorJpaEntity> findByTenantIdOrderByDisplayNameAscIdAsc(UUID tenantId);
}
