package io.gnomon.booking.application.port.in;

import java.time.Instant;
import java.util.UUID;

public interface AdminAppointmentQueryUseCase {
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
}
