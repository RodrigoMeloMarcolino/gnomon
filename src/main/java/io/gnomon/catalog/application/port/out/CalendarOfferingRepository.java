package io.gnomon.catalog.application.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CalendarOfferingRepository {

  void replace(UUID tenantId, UUID calendarId, Set<UUID> offeringIds);

  List<UUID> findCalendarIdsByTenantIdAndOfferingId(UUID tenantId, UUID offeringId);
}
