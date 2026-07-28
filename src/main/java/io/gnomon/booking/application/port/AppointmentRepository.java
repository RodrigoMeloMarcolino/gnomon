package io.gnomon.booking.application.port;

import io.gnomon.booking.domain.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

  Optional<Appointment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  boolean insert(Appointment appointment);

  void insertSlots(
      UUID tenantId, UUID appointmentId, UUID calendarId, List<Instant> slotStarts);
}
