package io.gnomon.availability.infrastructure.persistence.repository;

import io.gnomon.availability.infrastructure.persistence.entity.AvailabilityRuleJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAvailabilityRuleRepository
    extends JpaRepository<AvailabilityRuleJpaEntity, UUID> {

  Optional<AvailabilityRuleJpaEntity> findByTenantIdAndId(UUID tenantId, UUID id);

  List<AvailabilityRuleJpaEntity> findByTenantIdAndCalendarIdOrderByWeekdayAscStartTimeAscIdAsc(
      UUID tenantId, UUID calendarId);

  List<AvailabilityRuleJpaEntity>
      findByTenantIdAndCalendarIdAndWeekdayAndActiveTrueOrderByStartTimeAscIdAsc(
          UUID tenantId, UUID calendarId, short weekday);
}
