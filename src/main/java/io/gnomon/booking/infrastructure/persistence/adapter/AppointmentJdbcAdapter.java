package io.gnomon.booking.infrastructure.persistence.adapter;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.out.AppointmentRepository;
import io.gnomon.booking.domain.model.Appointment;
import io.gnomon.booking.infrastructure.persistence.support.BookingConstraintNames;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AppointmentJdbcAdapter implements AppointmentRepository {

  public static final String SELECT_BY_IDEMPOTENCY_KEY =
      """
      SELECT id,
             tenant_id,
             calendar_id,
             offering_id,
             customer_id,
             start_at,
             end_at,
             duration_minutes_snapshot,
             calendar_timezone_snapshot,
             status,
             customer_notes,
             idempotency_key,
             idempotency_fingerprint
      FROM appointments
      WHERE tenant_id = ?
        AND idempotency_key = ?
      """;

  public static final String INSERT_APPOINTMENT =
      """
      INSERT INTO appointments (
          id,
          tenant_id,
          calendar_id,
          offering_id,
          customer_id,
          start_at,
          end_at,
          duration_minutes_snapshot,
          calendar_timezone_snapshot,
          status,
          customer_notes,
          idempotency_key,
          idempotency_fingerprint
      )
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT ON CONSTRAINT uq_appointments_tenant_idempotency_key DO NOTHING
      """;

  public static final String INSERT_SLOT =
      """
      INSERT INTO appointment_slots (
          tenant_id,
          appointment_id,
          calendar_id,
          slot_start_at
      )
      VALUES (?, ?, ?, ?)
      """;

  private final JdbcTemplate jdbcTemplate;

  public AppointmentJdbcAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<Appointment> findByTenantIdAndIdempotencyKey(
      UUID tenantId, String idempotencyKey) {
    return jdbcTemplate
        .query(
            SELECT_BY_IDEMPOTENCY_KEY,
            AppointmentJdbcAdapter::mapAppointment,
            tenantId,
            idempotencyKey)
        .stream()
        .findFirst();
  }

  @Override
  public boolean insert(Appointment appointment) {
    try {
      return jdbcTemplate.update(
              INSERT_APPOINTMENT,
              appointment.id(),
              appointment.tenantId(),
              appointment.calendarId(),
              appointment.offeringId(),
              appointment.customerId(),
              Timestamp.from(appointment.startAt()),
              Timestamp.from(appointment.endAt()),
              appointment.durationMinutesSnapshot(),
              appointment.calendarTimezoneSnapshot(),
              appointment.status().name().toLowerCase(Locale.ROOT),
              appointment.customerNotes(),
              appointment.idempotencyKey(),
              appointment.idempotencyFingerprint())
          == 1;
    } catch (DataIntegrityViolationException exception) {
      if (isKnownAppointmentValidationConstraint(exception)) {
        throw new BookingException(
            "validation_error", "appointment data violates a database constraint");
      }
      throw exception;
    }
  }

  @Override
  public void insertSlots(
      UUID tenantId, UUID appointmentId, UUID calendarId, List<Instant> slotStarts) {
    if (slotStarts.isEmpty()) {
      return;
    }

    List<Object[]> arguments =
        slotStarts.stream()
            .map(
                slotStart ->
                    new Object[] {tenantId, appointmentId, calendarId, Timestamp.from(slotStart)})
            .toList();

    try {
      jdbcTemplate.batchUpdate(INSERT_SLOT, arguments);
    } catch (DataIntegrityViolationException exception) {
      if (BookingConstraintNames.contains(exception, "pk_appointment_slots")) {
        throw new BookingException("slot_unavailable", "slot is no longer available");
      }
      if (isKnownSlotValidationConstraint(exception)) {
        throw new BookingException(
            "validation_error", "appointment slot data violates a database constraint");
      }
      throw exception;
    }
  }

  private static Appointment mapAppointment(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new Appointment(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getObject("calendar_id", UUID.class),
        resultSet.getObject("offering_id", UUID.class),
        resultSet.getObject("customer_id", UUID.class),
        resultSet.getTimestamp("start_at").toInstant(),
        resultSet.getTimestamp("end_at").toInstant(),
        resultSet.getInt("duration_minutes_snapshot"),
        resultSet.getString("calendar_timezone_snapshot"),
        Appointment.Status.valueOf(resultSet.getString("status").toUpperCase(Locale.ROOT)),
        resultSet.getString("customer_notes"),
        resultSet.getString("idempotency_key"),
        resultSet.getString("idempotency_fingerprint"));
  }

  private static boolean isKnownAppointmentValidationConstraint(Throwable exception) {
    return BookingConstraintNames.contains(exception, "uq_appointments_tenant_identity")
        || BookingConstraintNames.contains(exception, "fk_appointments_tenant")
        || BookingConstraintNames.contains(exception, "fk_appointments_tenant_calendar")
        || BookingConstraintNames.contains(exception, "fk_appointments_tenant_offering")
        || BookingConstraintNames.contains(exception, "fk_appointments_customer")
        || BookingConstraintNames.contains(exception, "ck_appointments_time_order")
        || BookingConstraintNames.contains(exception, "ck_appointments_duration_snapshot")
        || BookingConstraintNames.contains(exception, "ck_appointments_timezone_not_blank")
        || BookingConstraintNames.contains(exception, "ck_appointments_status")
        || BookingConstraintNames.contains(exception, "ck_appointments_idempotency_key_not_blank")
        || BookingConstraintNames.contains(exception, "ck_appointments_idempotency_fingerprint");
  }

  private static boolean isKnownSlotValidationConstraint(Throwable exception) {
    return BookingConstraintNames.contains(exception, "fk_appointment_slots_tenant")
        || BookingConstraintNames.contains(exception, "fk_appointment_slots_tenant_appointment")
        || BookingConstraintNames.contains(exception, "fk_appointment_slots_tenant_calendar");
  }
}
