package io.gnomon.tenancy.application.port.out;

import io.gnomon.tenancy.domain.model.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {

  Tenant save(Tenant tenant);

  Optional<Tenant> findBySlug(String slug);

  Optional<Tenant> findById(UUID id);

  List<Tenant> findByMemberUserId(UUID userId);
}
