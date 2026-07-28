package io.gnomon.availability.application.port;

import io.gnomon.availability.domain.AvailabilityRule;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityRuleRepository {

  AvailabilityRule save(AvailabilityRule rule);

  Optional<AvailabilityRule> findById(UUID id);

  Optional<AvailabilityRule> findByTenantIdAndId(UUID tenantId, UUID id);

  List<AvailabilityRule> findByTenantIdAndCalendarId(UUID tenantId, UUID calendarId);

  List<AvailabilityRule> findActiveByTenantIdAndCalendarIdAndWeekday(
      UUID tenantId, UUID calendarId, DayOfWeek weekday);
}
