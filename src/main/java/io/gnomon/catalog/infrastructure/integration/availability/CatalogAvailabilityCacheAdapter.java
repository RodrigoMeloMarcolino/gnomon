package io.gnomon.catalog.infrastructure.integration.availability;

import io.gnomon.availability.application.port.in.AvailabilityCacheInvalidationUseCase;
import io.gnomon.catalog.application.port.out.CatalogAvailabilityCachePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class CatalogAvailabilityCacheAdapter implements CatalogAvailabilityCachePort {

  private final AvailabilityCacheInvalidationUseCase availability;

  CatalogAvailabilityCacheAdapter(AvailabilityCacheInvalidationUseCase availability) {
    this.availability = availability;
  }

  @Override
  public void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId) {
    availability.invalidateCalendarAfterCommit(tenantId, calendarId);
  }
}
