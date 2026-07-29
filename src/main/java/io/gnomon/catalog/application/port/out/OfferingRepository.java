package io.gnomon.catalog.application.port.out;

import io.gnomon.catalog.domain.model.Offering;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferingRepository {

  Offering save(Offering offering);

  Optional<Offering> findById(UUID id);

  Optional<Offering> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Offering> findByTenantId(UUID tenantId);

  List<Offering> findActiveByTenantId(UUID tenantId, UUID calendarId);

  boolean activeTitleExists(UUID tenantId, String normalizedTitle, UUID excludedOfferingId);
}
