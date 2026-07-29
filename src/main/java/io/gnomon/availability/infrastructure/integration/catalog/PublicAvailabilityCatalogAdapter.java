package io.gnomon.availability.infrastructure.integration.catalog;

import io.gnomon.availability.application.port.out.OfferingContext;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PublicAvailabilityCatalogAdapter implements PublicAvailabilityCatalogPort {

  private final SchedulableOfferingUseCase catalog;

  PublicAvailabilityCatalogAdapter(SchedulableOfferingUseCase catalog) {
    this.catalog = catalog;
  }

  @Override
  public OfferingContext requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
    var offering = catalog.requireSchedulableOffering(tenantSlug, calendarId, offeringId);
    return new OfferingContext(
        offering.tenantId(), offering.calendarId(), offering.zoneId(), offering.durationMinutes());
  }
}
