package io.gnomon.availability.application.port.out;

import java.time.ZoneId;
import java.util.UUID;

public interface PublicAvailabilityCatalogPort {

  OfferingContext requireSchedulableOffering(String tenantSlug, UUID calendarId, UUID offeringId);

  record OfferingContext(UUID tenantId, UUID calendarId, ZoneId zoneId, int durationMinutes) {}
}
