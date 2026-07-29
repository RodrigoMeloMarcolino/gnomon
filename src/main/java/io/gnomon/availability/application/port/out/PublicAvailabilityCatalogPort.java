package io.gnomon.availability.application.port.out;

import java.util.UUID;

public interface PublicAvailabilityCatalogPort {

  OfferingContext requireSchedulableOffering(String tenantSlug, UUID calendarId, UUID offeringId);
}
