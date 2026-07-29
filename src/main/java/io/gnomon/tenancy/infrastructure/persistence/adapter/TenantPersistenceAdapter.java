package io.gnomon.tenancy.infrastructure.persistence.adapter;

import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.infrastructure.persistence.entity.TenantJpaEntity;
import io.gnomon.tenancy.infrastructure.persistence.repository.SpringDataTenantRepository;
import io.gnomon.tenancy.infrastructure.persistence.support.ConstraintNames;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class TenantPersistenceAdapter implements TenantRepository {

  private final SpringDataTenantRepository repository;

  TenantPersistenceAdapter(SpringDataTenantRepository repository) {
    this.repository = repository;
  }

  @Override
  public Tenant save(Tenant tenant) {
    try {
      return repository.saveAndFlush(TenantJpaEntity.from(tenant)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (ConstraintNames.contains(exception, "uq_tenants_slug")) {
        throw new TenancyException("tenant_slug_taken", "tenant slug is already in use");
      }
      throw exception;
    }
  }

  @Override
  public Optional<Tenant> findBySlug(String slug) {
    return repository.findBySlug(slug).map(TenantJpaEntity::toDomain);
  }

  @Override
  public Optional<Tenant> findById(UUID id) {
    return repository.findById(id).map(TenantJpaEntity::toDomain);
  }

  @Override
  public List<Tenant> findByMemberUserId(UUID userId) {
    return repository.findByMemberUserId(userId).stream().map(TenantJpaEntity::toDomain).toList();
  }
}
