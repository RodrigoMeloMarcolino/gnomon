package io.gnomon.booking.application.port.out;

import java.time.ZoneId;
import java.util.UUID;

public interface BookingCatalogPort {

  BookingContext requireSchedulableOffering(String tenantSlug, UUID calendarId, UUID offeringId);

  record BookingContext(
      UUID tenantId,
      UUID calendarId,
      String calendarName,
      ZoneId zoneId,
      UUID offeringId,
      String offeringTitle,
      int durationMinutes,
      Integer priceCents) {}
}
