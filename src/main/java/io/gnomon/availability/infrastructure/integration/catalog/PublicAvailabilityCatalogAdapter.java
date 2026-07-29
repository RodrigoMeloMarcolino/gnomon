package io.gnomon.availability.infrastructure.integration.catalog;

import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort;
import io.gnomon.availability.application.port.out.PublicAvailabilityCatalogPort.OfferingContext;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase;
import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.OfferingRepository;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Calendar;
import io.gnomon.catalog.domain.model.Offering;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PublicAvailabilityCatalogAdapter implements PublicAvailabilityCatalogPort {

  private final SchedulableOfferingUseCase catalog;

  PublicAvailabilityCatalogAdapter(SchedulableOfferingUseCase catalog) {
    this.catalog = catalog;
  }

  /** Compatibility seam for the existing adapter tests; production wiring uses the input port. */
  PublicAvailabilityCatalogAdapter(
      CatalogTenantAccessPort tenantAccess,
      CalendarRepository calendars,
      OfferingRepository offerings) {
    this(
        (tenantSlug, calendarId, offeringId) -> {
          var tenant = tenantAccess.requirePublicTenant(tenantSlug);
          Calendar calendar =
              calendars
                  .findByTenantIdAndId(tenant.tenantId(), calendarId)
                  .filter(Calendar::active)
                  .orElseThrow(
                      () -> new CatalogException("calendar_not_found", "calendar was not found"));
          Offering offering =
              offerings.findActiveByTenantId(tenant.tenantId(), calendarId).stream()
                  .filter(candidate -> candidate.id().equals(offeringId))
                  .findFirst()
                  .orElseThrow(
                      () -> new CatalogException("offering_not_found", "offering was not found"));
          return new SchedulableOfferingUseCase.SchedulableOffering(
              tenant.tenantId(),
              calendar.id(),
              calendar.name(),
              ZoneId.of(calendar.timezone()),
              offering.id(),
              offering.title(),
              offering.durationMinutes(),
              offering.priceCents());
        });
  }

  @Override
  public OfferingContext requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
    var offering = catalog.requireSchedulableOffering(tenantSlug, calendarId, offeringId);
    return new OfferingContext(
        offering.tenantId(), offering.calendarId(), offering.zoneId(), offering.durationMinutes());
  }
}
