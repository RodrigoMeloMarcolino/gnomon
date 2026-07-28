package io.gnomon.booking.application;

import java.time.Instant;
import java.util.UUID;

public interface CreateAppointmentUseCase {

  CreationResult create(CreateAppointmentCommand command);

  record CreateAppointmentCommand(
      String tenantSlug,
      String idempotencyKey,
      UUID calendarId,
      UUID offeringId,
      Instant startAt,
      String customerName,
      String customerPhone,
      String customerEmail,
      String customerNotes) {}

  record CreationResult(AppointmentResult appointment, boolean replayed) {}
}
