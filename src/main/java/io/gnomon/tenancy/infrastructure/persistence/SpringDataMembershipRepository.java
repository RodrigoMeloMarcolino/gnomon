package io.gnomon.tenancy.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {

  Optional<MembershipJpaEntity> findByTenantIdAndUserId(UUID tenantId, UUID userId);

  List<MembershipJpaEntity> findByUserIdOrderByCreatedAt(UUID userId);

  List<MembershipJpaEntity> findByTenantIdOrderByCreatedAt(UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select membership
      from MembershipJpaEntity membership
      where membership.tenantId = :tenantId
      order by membership.id
      """)
  List<MembershipJpaEntity> lockByTenantId(@Param("tenantId") UUID tenantId);
}
