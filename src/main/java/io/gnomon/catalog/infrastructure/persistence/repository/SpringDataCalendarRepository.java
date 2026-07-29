package io.gnomon.catalog.infrastructure.persistence.repository;

import io.gnomon.catalog.infrastructure.persistence.entity.CalendarJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCalendarRepository extends JpaRepository<CalendarJpaEntity, UUID> {

  Optional<CalendarJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<CalendarJpaEntity> findByTenantIdAndCollaboratorId(UUID tenantId, UUID collaboratorId);

  List<CalendarJpaEntity> findByTenantIdOrderByNameAscIdAsc(UUID tenantId);

  List<CalendarJpaEntity> findByTenantIdAndActiveTrueOrderByNameAscIdAsc(UUID tenantId);
}
