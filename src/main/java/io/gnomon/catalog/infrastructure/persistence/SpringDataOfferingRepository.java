package io.gnomon.catalog.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOfferingRepository extends JpaRepository<OfferingJpaEntity, UUID> {

  Optional<OfferingJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  List<OfferingJpaEntity> findByTenantIdOrderByTitleAsc(UUID tenantId);

  List<OfferingJpaEntity> findByTenantIdAndActiveTrueOrderByTitleAsc(UUID tenantId);

  @Query(
      """
      select o
      from OfferingJpaEntity o
      where o.tenantId = :tenantId
        and o.active = true
        and exists (
          select co.id
          from CalendarOfferingJpaEntity co
          where co.tenantId = :tenantId
            and co.id.calendarId = :calendarId
            and co.id.offeringId = o.id
        )
      order by o.title asc
      """)
  List<OfferingJpaEntity> findActiveAssignedToCalendar(
      @Param("tenantId") UUID tenantId, @Param("calendarId") UUID calendarId);

  @Query(
      """
      select (count(o) > 0)
      from OfferingJpaEntity o
      where o.tenantId = :tenantId
        and o.active = true
        and lower(o.title) = :normalizedTitle
        and o.id <> :excludedOfferingId
      """)
  boolean activeTitleExists(
      @Param("tenantId") UUID tenantId,
      @Param("normalizedTitle") String normalizedTitle,
      @Param("excludedOfferingId") UUID excludedOfferingId);
}
