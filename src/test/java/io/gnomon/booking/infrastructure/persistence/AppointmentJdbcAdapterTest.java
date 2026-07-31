package io.gnomon.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.domain.model.Appointment;
import io.gnomon.booking.infrastructure.persistence.adapter.AppointmentJdbcAdapter;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AppointmentJdbcAdapterTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void insert_whenIdempotencyKeyWins_shouldReturnTrue() {
    Appointment appointment = appointment();
    when(jdbcTemplate.update(eq(AppointmentJdbcAdapter.INSERT_APPOINTMENT), any(Object[].class)))
        .thenReturn(1);
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    assertThat(adapter.insert(appointment)).isTrue();
  }

  @Test
  void insert_whenIdempotencyKeyAlreadyExists_shouldReturnFalseWithoutException() {
    Appointment appointment = appointment();
    when(jdbcTemplate.update(eq(AppointmentJdbcAdapter.INSERT_APPOINTMENT), any(Object[].class)))
        .thenReturn(0);
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    assertThat(adapter.insert(appointment)).isFalse();
    assertThat(AppointmentJdbcAdapter.INSERT_APPOINTMENT)
        .contains("ON CONFLICT ON CONSTRAINT uq_appointments_tenant_idempotency_key DO NOTHING");
  }

  @Test
  void insert_whenTenantIdentityConstraintFails_shouldTranslateValidationError() {
    Appointment appointment = appointment();
    var failure =
        new DataIntegrityViolationException(
            "insert failed",
            new SQLException("violates constraint uq_appointments_tenant_identity"));
    when(jdbcTemplate.update(eq(AppointmentJdbcAdapter.INSERT_APPOINTMENT), any(Object[].class)))
        .thenThrow(failure);
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    assertThatThrownBy(() -> adapter.insert(appointment))
        .isInstanceOfSatisfying(
            BookingException.class,
            exception -> assertThat(exception.code()).isEqualTo("validation_error"));
  }

  @Test
  void findByTenantIdAndIdempotencyKey_whenPresent_shouldReturnAppointment() {
    Appointment appointment = appointment();
    when(jdbcTemplate.query(
            eq(AppointmentJdbcAdapter.SELECT_BY_IDEMPOTENCY_KEY),
            any(RowMapper.class),
            eq(appointment.tenantId()),
            eq(appointment.idempotencyKey())))
        .thenReturn(List.of(appointment));
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    Optional<Appointment> result =
        adapter.findByTenantIdAndIdempotencyKey(
            appointment.tenantId(), appointment.idempotencyKey());

    assertThat(result).containsSame(appointment);
  }

  @Test
  void insertSlots_whenSlotUniqueConstraintFails_shouldTranslateSlotUnavailable() {
    Appointment appointment = appointment();
    var failure =
        new DataIntegrityViolationException(
            "batch failed", new SQLException("violates constraint PK_APPOINTMENT_SLOTS"));
    when(jdbcTemplate.batchUpdate(eq(AppointmentJdbcAdapter.INSERT_SLOT), any(List.class)))
        .thenThrow(failure);
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    assertThatThrownBy(
            () ->
                adapter.insertSlots(
                    appointment.tenantId(),
                    appointment.id(),
                    appointment.calendarId(),
                    List.of(appointment.startAt())))
        .isInstanceOfSatisfying(
            BookingException.class,
            exception -> assertThat(exception.code()).isEqualTo("slot_unavailable"));
  }

  @Test
  void insertSlots_whenSlotsExist_shouldUseOneJdbcBatch() {
    Appointment appointment = appointment();
    Instant secondSlot = appointment.startAt().plusSeconds(900);
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    adapter.insertSlots(
        appointment.tenantId(),
        appointment.id(),
        appointment.calendarId(),
        List.of(appointment.startAt(), secondSlot));

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
    verify(jdbcTemplate).batchUpdate(eq(AppointmentJdbcAdapter.INSERT_SLOT), batchCaptor.capture());
    assertThat(batchCaptor.getValue()).hasSize(2);
    assertThat(batchCaptor.getValue().getFirst())
        .containsExactly(
            appointment.tenantId(),
            appointment.id(),
            appointment.calendarId(),
            java.sql.Timestamp.from(appointment.startAt()));
  }

  @Test
  void insertSlots_whenListIsEmpty_shouldNotCallDatabase() {
    Appointment appointment = appointment();
    var adapter = new AppointmentJdbcAdapter(jdbcTemplate);

    adapter.insertSlots(
        appointment.tenantId(), appointment.id(), appointment.calendarId(), List.of());

    verify(jdbcTemplate, never()).batchUpdate(any(String.class), any(List.class));
  }

  private static Appointment appointment() {
    Instant startAt = Instant.parse("2027-07-01T12:00:00Z");
    return new Appointment(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        startAt,
        startAt.plusSeconds(1800),
        30,
        "America/Fortaleza",
        Appointment.Status.SCHEDULED,
        "window seat",
        "key-1",
        "a".repeat(64));
  }
}
