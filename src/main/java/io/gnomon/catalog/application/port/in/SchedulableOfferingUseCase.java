package io.gnomon.catalog.application.port.in;

import java.time.ZoneId;
import java.util.UUID;

/** Public cross-module contract for resolving an active calendar/offering snapshot. */
public interface SchedulableOfferingUseCase {

  SchedulableOffering requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId);

  record SchedulableOffering(
      UUID tenantId,
      UUID calendarId,
      String calendarName,
      ZoneId zoneId,
      UUID offeringId,
      String offeringTitle,
      int durationMinutes,
      Integer priceCents) {}
}
