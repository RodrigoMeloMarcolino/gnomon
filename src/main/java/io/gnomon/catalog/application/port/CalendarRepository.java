package io.gnomon.catalog.application.port;

import io.gnomon.catalog.domain.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarRepository {

  Calendar save(Calendar calendar);

  Optional<Calendar> findById(UUID id);

  Optional<Calendar> findByTenantIdAndId(UUID tenantId, UUID id);

  Optional<Calendar> findByTenantIdAndCollaboratorId(UUID tenantId, UUID collaboratorId);

  List<Calendar> findByTenantId(UUID tenantId);

  List<Calendar> findActiveByTenantId(UUID tenantId);
}
