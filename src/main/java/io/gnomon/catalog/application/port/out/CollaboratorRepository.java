package io.gnomon.catalog.application.port.out;

import io.gnomon.catalog.domain.model.Collaborator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollaboratorRepository {

  Collaborator save(Collaborator collaborator);

  Optional<Collaborator> findById(UUID id);

  Optional<Collaborator> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Collaborator> findByTenantId(UUID tenantId);
}
