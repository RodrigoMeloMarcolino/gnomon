package io.gnomon.booking.application.port.out;

import io.gnomon.booking.domain.model.Appointment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

  Optional<Appointment> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

  boolean insert(Appointment appointment);

  void insertSlots(UUID tenantId, UUID appointmentId, UUID calendarId, List<Instant> slotStarts);

  Optional<Appointment> findByTenantIdAndIdForUpdate(UUID tenantId, UUID id);

  boolean existsById(UUID id);

  void updateStatus(UUID tenantId, UUID id, Appointment.Status status);

  void deleteSlots(UUID tenantId, UUID appointmentId);
}
