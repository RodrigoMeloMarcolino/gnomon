package io.gnomon.catalog.application.port.out;

import java.util.UUID;

/** Notifies availability through a module adapter after catalog mutations commit. */
public interface CatalogAvailabilityCachePort {

  void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId);
}
