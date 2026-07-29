package io.gnomon.booking.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record CreateAppointmentCommand(
    String tenantSlug,
    String idempotencyKey,
    UUID calendarId,
    UUID offeringId,
    Instant startAt,
    String customerName,
    String customerPhone,
    String customerEmail,
    String customerNotes) {}
