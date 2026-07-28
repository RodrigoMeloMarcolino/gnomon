package io.gnomon.tenancy.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMembershipRepository extends JpaRepository<MembershipJpaEntity, UUID> {

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO tenant_memberships (id, tenant_id, user_id, role, created_at, updated_at)
          VALUES (:id, :tenantId, :userId, 'staff', :createdAt, :updatedAt)
          ON CONFLICT (tenant_id, user_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertStaffIfAbsent(
      @Param("id") UUID id,
      @Param("tenantId") UUID tenantId,
      @Param("userId") UUID userId,
      @Param("createdAt") java.time.Instant createdAt,
      @Param("updatedAt") java.time.Instant updatedAt);

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
