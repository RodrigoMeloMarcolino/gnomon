package io.gnomon.catalog.application.port.in;

import java.util.UUID;

/** Public cross-module contract for resolving an active calendar/offering snapshot. */
public interface SchedulableOfferingUseCase {

  SchedulableOffering requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId);
}
