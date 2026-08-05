package io.gnomon.booking.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface AdminAppointmentUseCase {
  AdminAppointmentPage list(
      UUID actorUserId,
      String tenantSlug,
      Instant from,
      Instant to,
      UUID calendarId,
      String status,
      int page,
      int size);

  AdminAppointment get(UUID actorUserId, String tenantSlug, UUID id);

  AdminAppointment transition(UUID actorUserId, String tenantSlug, UUID id, Transition transition);

  enum Transition {
    CANCEL,
    COMPLETE,
    NO_SHOW
  }
}
