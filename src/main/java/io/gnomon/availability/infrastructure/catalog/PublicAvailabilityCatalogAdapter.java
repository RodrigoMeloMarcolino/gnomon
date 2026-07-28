package io.gnomon.availability.infrastructure.catalog;

import io.gnomon.availability.application.port.PublicAvailabilityCatalogPort;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.OfferingRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.catalog.domain.Offering;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class PublicAvailabilityCatalogAdapter implements PublicAvailabilityCatalogPort {

  private final CatalogTenantAccessPort tenantAccess;
  private final CalendarRepository calendars;
  private final OfferingRepository offerings;

  PublicAvailabilityCatalogAdapter(
      CatalogTenantAccessPort tenantAccess,
      CalendarRepository calendars,
      OfferingRepository offerings) {
    this.tenantAccess = tenantAccess;
    this.calendars = calendars;
    this.offerings = offerings;
  }

  @Override
  public OfferingContext requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
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
    return new OfferingContext(
        tenant.tenantId(),
        calendar.id(),
        ZoneId.of(calendar.timezone()),
        offering.durationMinutes());
  }
}
