package io.gnomon.catalog.application.port.in;

import java.time.ZoneId;
import java.util.UUID;

public record SchedulableOffering(
    UUID tenantId,
    UUID calendarId,
    String calendarName,
    ZoneId zoneId,
    UUID offeringId,
    String offeringTitle,
    int durationMinutes,
    Integer priceCents) {}
