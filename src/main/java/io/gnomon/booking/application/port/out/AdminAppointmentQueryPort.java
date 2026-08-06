package io.gnomon.booking.application.port.out;

import io.gnomon.booking.application.port.in.AdminAppointment;
import io.gnomon.booking.application.port.in.AdminAppointmentPage;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AdminAppointmentQueryPort {
  AdminAppointmentPage findPage(
      UUID tenantId, Instant from, Instant to, UUID calendarId, String status, int page, int size);

  Optional<AdminAppointment> findByTenantIdAndId(UUID tenantId, UUID id);

  boolean existsById(UUID id);
}
