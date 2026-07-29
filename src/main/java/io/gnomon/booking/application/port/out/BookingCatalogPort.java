package io.gnomon.booking.application.port.out;

import java.util.UUID;

public interface BookingCatalogPort {

  BookingContext requireSchedulableOffering(String tenantSlug, UUID calendarId, UUID offeringId);
}
