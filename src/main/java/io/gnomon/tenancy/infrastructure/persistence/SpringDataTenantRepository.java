package io.gnomon.tenancy.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTenantRepository extends JpaRepository<TenantJpaEntity, UUID> {

  Optional<TenantJpaEntity> findBySlug(String slug);

  @Query(
      """
      select tenant
      from TenantJpaEntity tenant
      join MembershipJpaEntity membership on membership.tenantId = tenant.id
      where membership.userId = :userId
      order by tenant.name, tenant.id
      """)
  List<TenantJpaEntity> findByMemberUserId(@Param("userId") UUID userId);
}
