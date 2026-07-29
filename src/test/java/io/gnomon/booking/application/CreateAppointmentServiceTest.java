package io.gnomon.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.in.CreateAppointmentCommand;
import io.gnomon.booking.application.port.out.AppointmentFingerprint;
import io.gnomon.booking.application.port.out.AppointmentRepository;
import io.gnomon.booking.application.port.out.BookingAvailabilityPort;
import io.gnomon.booking.application.port.out.BookingCatalogPort;
import io.gnomon.booking.application.port.out.BookingContext;
import io.gnomon.booking.application.port.out.NormalizedBooking;
import io.gnomon.booking.application.port.out.PhoneCanonicalizer;
import io.gnomon.booking.application.service.CreateAppointmentService;
import io.gnomon.booking.domain.model.Appointment;
import io.gnomon.booking.domain.model.Appointment.Status;
import io.gnomon.booking.domain.service.SlotGenerator;
import io.gnomon.customers.application.port.out.CustomerRepository;
import io.gnomon.customers.domain.model.Customer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateAppointmentServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID APPOINTMENT_ID =
      UUID.fromString("60000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2027-06-30T12:00:00Z");
  private static final Instant START_AT = Instant.parse("2027-07-01T12:00:00Z");
  private static final String FINGERPRINT = "a".repeat(64);

  @Mock private BookingCatalogPort catalog;
  @Mock private BookingAvailabilityPort availability;
  @Mock private CustomerRepository customers;
  @Mock private AppointmentRepository appointments;
  @Mock private PhoneCanonicalizer phoneCanonicalizer;
  @Mock private AppointmentFingerprint appointmentFingerprint;
  @Mock private SlotGenerator slotGenerator;

  private CreateAppointmentService service;

  @BeforeEach
  void setUp() {
    service =
        new CreateAppointmentService(
            catalog,
            availability,
            customers,
            appointments,
            phoneCanonicalizer,
            appointmentFingerprint,
            slotGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC));
    when(catalog.requireSchedulableOffering("barbearia-solar", CALENDAR_ID, OFFERING_ID))
        .thenReturn(context());
  }

  @Test
  void create_whenRequestIsNew_shouldPersistSnapshotAndOccupiedSlots() {
    givenNormalizedRequest();
    when(appointments.findByTenantIdAndIdempotencyKey(TENANT_ID, "intent-1"))
        .thenReturn(Optional.empty());
    when(availability.isAvailable(
            TENANT_ID, CALENDAR_ID, 30, ZoneId.of("America/Fortaleza"), START_AT, NOW))
        .thenReturn(true);
    when(customers.findOrCreate("Ana", "+5585999999999", "ana@example.com")).thenReturn(customer());
    when(appointments.insert(any(Appointment.class))).thenReturn(true);
    when(slotGenerator.generate(START_AT, 30))
        .thenReturn(List.of(START_AT, START_AT.plusSeconds(900)));

    var result = service.create(command());

    assertThat(result.replayed()).isFalse();
    assertThat(result.appointment().startAt()).isEqualTo(START_AT);
    assertThat(result.appointment().endAt()).isEqualTo(START_AT.plusSeconds(1800));
    assertThat(result.appointment().status()).isEqualTo("scheduled");
    assertThat(result.appointment().customer().phone()).isEqualTo("+5585999999999");

    var appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
    verify(appointments).insert(appointmentCaptor.capture());
    Appointment persisted = appointmentCaptor.getValue();
    assertThat(persisted.durationMinutesSnapshot()).isEqualTo(30);
    assertThat(persisted.calendarTimezoneSnapshot()).isEqualTo("America/Fortaleza");
    assertThat(persisted.customerNotes()).isEqualTo("Janela");
    assertThat(persisted.idempotencyFingerprint()).isEqualTo(FINGERPRINT);
    verify(appointmentFingerprint)
        .sha256(
            new NormalizedBooking(
                CALENDAR_ID.toString(),
                OFFERING_ID.toString(),
                START_AT.toString(),
                "Ana",
                "+5585999999999",
                "ana@example.com",
                "Janela"));
    verify(appointments)
        .insertSlots(
            TENANT_ID, persisted.id(), CALENDAR_ID, List.of(START_AT, START_AT.plusSeconds(900)));
  }

  @Test
  void create_whenIdempotencyKeyAlreadyHasSameFingerprint_shouldReplayWithoutAvailabilityCheck() {
    givenNormalizedRequest();
    Appointment existing = appointment(FINGERPRINT);
    when(appointments.findByTenantIdAndIdempotencyKey(TENANT_ID, "intent-1"))
        .thenReturn(Optional.of(existing));
    when(customers.findOrCreate("Ana", "+5585999999999", "ana@example.com")).thenReturn(customer());

    var result = service.create(command());

    assertThat(result.replayed()).isTrue();
    assertThat(result.appointment().id()).isEqualTo(APPOINTMENT_ID);
    verify(availability, never()).isAvailable(any(), any(), anyInt(), any(), any(), any());
    verify(appointments, never()).insert(any());
    verify(appointments, never()).insertSlots(any(), any(), any(), any());
  }

  @Test
  void create_whenIdempotencyKeyAlreadyHasDifferentFingerprint_shouldConflict() {
    givenNormalizedRequest();
    when(appointments.findByTenantIdAndIdempotencyKey(TENANT_ID, "intent-1"))
        .thenReturn(Optional.of(appointment("b".repeat(64))));

    assertThatThrownBy(() -> service.create(command()))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("idempotency_key_conflict");

    verify(customers, never()).findOrCreate(any(), any(), any());
    verify(appointments, never()).insert(any());
  }

  @Test
  void create_whenConcurrentInsertUsesSamePayload_shouldReplayWinner() {
    givenNormalizedRequest();
    Appointment winner = appointment(FINGERPRINT);
    when(appointments.findByTenantIdAndIdempotencyKey(TENANT_ID, "intent-1"))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(availability.isAvailable(any(), any(), anyInt(), any(), any(), any())).thenReturn(true);
    when(customers.findOrCreate("Ana", "+5585999999999", "ana@example.com")).thenReturn(customer());
    when(appointments.insert(any())).thenReturn(false);

    var result = service.create(command());

    assertThat(result.replayed()).isTrue();
    assertThat(result.appointment().id()).isEqualTo(APPOINTMENT_ID);
    verify(appointments, never()).insertSlots(any(), any(), any(), any());
  }

  @Test
  void create_whenStartIsOutsideAvailability_shouldRejectBeforeCustomerWrite() {
    givenNormalizedRequest();
    when(appointments.findByTenantIdAndIdempotencyKey(TENANT_ID, "intent-1"))
        .thenReturn(Optional.empty());
    when(availability.isAvailable(any(), any(), anyInt(), any(), any(), any())).thenReturn(false);

    assertThatThrownBy(() -> service.create(command()))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo(CreateAppointmentService.SLOT_UNAVAILABLE_VALIDATION);

    verify(customers, never()).findOrCreate(any(), any(), any());
    verify(appointments, never()).insert(any());
  }

  @Test
  void create_whenStartContainsSeconds_shouldReturnValidationErrorWithoutTruncating() {
    CreateAppointmentCommand command =
        new CreateAppointmentCommand(
            "barbearia-solar",
            "intent-1",
            CALENDAR_ID,
            OFFERING_ID,
            START_AT.plusSeconds(45),
            "Ana",
            "(85) 99999-9999",
            "ANA@EXAMPLE.COM",
            "Janela");
    when(phoneCanonicalizer.canonicalize("(85) 99999-9999")).thenReturn("+5585999999999");

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("validation_error");

    verify(appointments, never()).findByTenantIdAndIdempotencyKey(any(), any());
  }

  private void givenNormalizedRequest() {
    when(phoneCanonicalizer.canonicalize("(85) 99999-9999")).thenReturn("+5585999999999");
    when(appointmentFingerprint.sha256(any())).thenReturn(FINGERPRINT);
  }

  private static CreateAppointmentCommand command() {
    return new CreateAppointmentCommand(
        "barbearia-solar",
        "intent-1",
        CALENDAR_ID,
        OFFERING_ID,
        START_AT,
        " Ana ",
        "(85) 99999-9999",
        " ANA@EXAMPLE.COM ",
        " Janela ");
  }

  private static BookingContext context() {
    return new BookingContext(
        TENANT_ID,
        CALENDAR_ID,
        "Agenda da Joana",
        ZoneId.of("America/Fortaleza"),
        OFFERING_ID,
        "Corte",
        30,
        4_500);
  }

  private static Customer customer() {
    return new Customer(CUSTOMER_ID, "Ana", "+5585999999999", "ana@example.com");
  }

  private static Appointment appointment(String fingerprint) {
    return new Appointment(
        APPOINTMENT_ID,
        TENANT_ID,
        CALENDAR_ID,
        OFFERING_ID,
        CUSTOMER_ID,
        START_AT,
        START_AT.plusSeconds(1800),
        30,
        "America/Fortaleza",
        Status.SCHEDULED,
        "Janela",
        "intent-1",
        fingerprint);
  }
}
