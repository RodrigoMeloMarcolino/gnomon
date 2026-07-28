package io.gnomon.tenancy.infrastructure.persistence;

import io.gnomon.tenancy.application.port.MembershipRepository;
import io.gnomon.tenancy.domain.TenancyException;
import io.gnomon.tenancy.domain.TenantMembership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class MembershipPersistenceAdapter implements MembershipRepository {

  private final SpringDataMembershipRepository repository;

  MembershipPersistenceAdapter(SpringDataMembershipRepository repository) {
    this.repository = repository;
  }

  @Override
  public TenantMembership save(TenantMembership membership) {
    try {
      return repository.saveAndFlush(MembershipJpaEntity.from(membership)).toDomain();
    } catch (DataIntegrityViolationException exception) {
      if (ConstraintNames.contains(exception, "uq_tenant_memberships_tenant_user")) {
        throw new TenancyException("membership_exists", "membership already exists");
      }
      throw exception;
    }
  }

  @Override
  public Optional<TenantMembership> findByTenantIdAndUserId(UUID tenantId, UUID userId) {
    return repository.findByTenantIdAndUserId(tenantId, userId).map(MembershipJpaEntity::toDomain);
  }

  @Override
  public Optional<TenantMembership> findById(UUID id) {
    return repository.findById(id).map(MembershipJpaEntity::toDomain);
  }

  @Override
  public List<TenantMembership> findByUserId(UUID userId) {
    return repository.findByUserIdOrderByCreatedAt(userId).stream()
        .map(MembershipJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<TenantMembership> findByTenantId(UUID tenantId) {
    return repository.findByTenantIdOrderByCreatedAt(tenantId).stream()
        .map(MembershipJpaEntity::toDomain)
        .toList();
  }

  @Override
  public List<TenantMembership> lockByTenantId(UUID tenantId) {
    return repository.lockByTenantId(tenantId).stream().map(MembershipJpaEntity::toDomain).toList();
  }

  @Override
  public void delete(TenantMembership membership) {
    repository.deleteById(membership.id());
    repository.flush();
  }
}
