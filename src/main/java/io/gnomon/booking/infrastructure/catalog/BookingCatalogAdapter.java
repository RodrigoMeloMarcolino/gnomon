package io.gnomon.booking.infrastructure.catalog;

import io.gnomon.booking.application.port.BookingCatalogPort;
import io.gnomon.booking.domain.BookingException;
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
class BookingCatalogAdapter implements BookingCatalogPort {

  private final CatalogTenantAccessPort tenantAccess;
  private final CalendarRepository calendars;
  private final OfferingRepository offerings;

  BookingCatalogAdapter(
      CatalogTenantAccessPort tenantAccess,
      CalendarRepository calendars,
      OfferingRepository offerings) {
    this.tenantAccess = tenantAccess;
    this.calendars = calendars;
    this.offerings = offerings;
  }

  @Override
  public BookingContext requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
    try {
      var tenant = tenantAccess.requirePublicTenant(tenantSlug);
      Calendar calendar =
          calendars
              .findByTenantIdAndId(tenant.tenantId(), calendarId)
              .filter(Calendar::active)
              .orElseThrow(
                  () -> new BookingException("calendar_not_found", "calendar was not found"));
      Offering offering =
          offerings.findActiveByTenantId(tenant.tenantId(), calendarId).stream()
              .filter(candidate -> candidate.id().equals(offeringId))
              .findFirst()
              .orElseThrow(
                  () -> new BookingException("offering_not_found", "offering was not found"));
      return new BookingContext(
          tenant.tenantId(),
          calendar.id(),
          calendar.name(),
          ZoneId.of(calendar.timezone()),
          offering.id(),
          offering.title(),
          offering.durationMinutes(),
          offering.priceCents());
    } catch (CatalogException exception) {
      throw new BookingException(exception.code(), exception.getMessage());
    }
  }
}
