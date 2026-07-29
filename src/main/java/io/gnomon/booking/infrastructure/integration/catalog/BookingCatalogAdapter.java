package io.gnomon.booking.infrastructure.integration.catalog;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.BookingCatalogPort;
import io.gnomon.booking.application.port.out.BookingContext;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase;
import io.gnomon.catalog.domain.exception.CatalogException;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingCatalogAdapter implements BookingCatalogPort {

  private final SchedulableOfferingUseCase catalog;

  BookingCatalogAdapter(SchedulableOfferingUseCase catalog) {
    this.catalog = catalog;
  }

  @Override
  public BookingContext requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
    try {
      var offering = catalog.requireSchedulableOffering(tenantSlug, calendarId, offeringId);
      return new BookingContext(
          offering.tenantId(),
          offering.calendarId(),
          offering.calendarName(),
          offering.zoneId(),
          offering.offeringId(),
          offering.offeringTitle(),
          offering.durationMinutes(),
          offering.priceCents());
    } catch (CatalogException exception) {
      throw new BookingException(exception.code(), exception.getMessage());
    }
  }
}
