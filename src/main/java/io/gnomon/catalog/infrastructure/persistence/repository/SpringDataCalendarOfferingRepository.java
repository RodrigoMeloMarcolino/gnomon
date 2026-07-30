package io.gnomon.catalog.infrastructure.persistence.repository;

import io.gnomon.catalog.infrastructure.persistence.entity.CalendarOfferingId;
import io.gnomon.catalog.infrastructure.persistence.entity.CalendarOfferingJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataCalendarOfferingRepository
    extends JpaRepository<CalendarOfferingJpaEntity, CalendarOfferingId> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      """
      delete from CalendarOfferingJpaEntity co
      where co.tenantId = :tenantId
        and co.id.calendarId = :calendarId
      """)
  void deleteAssignments(@Param("tenantId") UUID tenantId, @Param("calendarId") UUID calendarId);

  @Query(
      """
      select co.id.calendarId from CalendarOfferingJpaEntity co
      where co.tenantId = :tenantId and co.id.offeringId = :offeringId
      """)
  List<UUID> findCalendarIdsByTenantIdAndOfferingId(
      @Param("tenantId") UUID tenantId, @Param("offeringId") UUID offeringId);
}
