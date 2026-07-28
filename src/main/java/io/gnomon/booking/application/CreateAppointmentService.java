package io.gnomon.booking.application;

import io.gnomon.booking.application.CreateAppointmentUseCase.CreateAppointmentCommand;
import io.gnomon.booking.application.CreateAppointmentUseCase.CreationResult;
import io.gnomon.booking.application.port.AppointmentRepository;
import io.gnomon.booking.application.port.BookingAvailabilityPort;
import io.gnomon.booking.application.port.BookingCatalogPort;
import io.gnomon.booking.application.port.BookingCatalogPort.BookingContext;
import io.gnomon.booking.application.port.CustomerRepository;
import io.gnomon.booking.domain.Appointment;
import io.gnomon.booking.domain.Appointment.Status;
import io.gnomon.booking.domain.AppointmentFingerprint;
import io.gnomon.booking.domain.AppointmentFingerprint.NormalizedBooking;
import io.gnomon.booking.domain.BookingException;
import io.gnomon.booking.domain.Customer;
import io.gnomon.booking.domain.PhoneCanonicalizer;
import io.gnomon.booking.domain.SlotGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class CreateAppointmentService implements CreateAppointmentUseCase {

  public static final String SLOT_UNAVAILABLE_VALIDATION = "slot_unavailable_validation";

  private static final Logger LOGGER = LoggerFactory.getLogger(CreateAppointmentService.class);
  private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 255;

  private final BookingCatalogPort catalog;
  private final BookingAvailabilityPort availability;
  private final CustomerRepository customers;
  private final AppointmentRepository appointments;
  private final PhoneCanonicalizer phoneCanonicalizer;
  private final AppointmentFingerprint appointmentFingerprint;
  private final SlotGenerator slotGenerator;
  private final Clock clock;

  @Autowired
  public CreateAppointmentService(
      BookingCatalogPort catalog,
      BookingAvailabilityPort availability,
      CustomerRepository customers,
      AppointmentRepository appointments,
      PhoneCanonicalizer phoneCanonicalizer,
      AppointmentFingerprint appointmentFingerprint,
      SlotGenerator slotGenerator) {
    this(
        catalog,
        availability,
        customers,
        appointments,
        phoneCanonicalizer,
        appointmentFingerprint,
        slotGenerator,
        Clock.systemUTC());
  }

  CreateAppointmentService(
      BookingCatalogPort catalog,
      BookingAvailabilityPort availability,
      CustomerRepository customers,
      AppointmentRepository appointments,
      PhoneCanonicalizer phoneCanonicalizer,
      AppointmentFingerprint appointmentFingerprint,
      SlotGenerator slotGenerator,
      Clock clock) {
    this.catalog = catalog;
    this.availability = availability;
    this.customers = customers;
    this.appointments = appointments;
    this.phoneCanonicalizer = phoneCanonicalizer;
    this.appointmentFingerprint = appointmentFingerprint;
    this.slotGenerator = slotGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public CreationResult create(CreateAppointmentCommand command) {
    UUID tenantId = null;
    UUID calendarId = command == null ? null : command.calendarId();
    UUID appointmentId = null;
    try {
      requireCommand(command);
      BookingContext context =
          catalog.requireSchedulableOffering(
              command.tenantSlug(), command.calendarId(), command.offeringId());
      tenantId = context.tenantId();
      calendarId = context.calendarId();

      String idempotencyKey = normalizeIdempotencyKey(command.idempotencyKey());
      String customerName = normalizeCustomerName(command.customerName());
      String customerPhone = phoneCanonicalizer.canonicalize(command.customerPhone());
      String customerEmail = normalizeOptional(command.customerEmail(), true);
      String customerNotes = normalizeOptional(command.customerNotes(), false);
      Instant now = clock.instant();
      validateStart(command.startAt(), now);

      String fingerprint =
          appointmentFingerprint.sha256(
              new NormalizedBooking(
                  context.calendarId().toString(),
                  context.offeringId().toString(),
                  command.startAt().toString(),
                  customerName,
                  customerPhone,
                  customerEmail,
                  customerNotes));

      var existing =
          appointments.findByTenantIdAndIdempotencyKey(context.tenantId(), idempotencyKey);
      if (existing.isPresent()) {
        appointmentId = existing.orElseThrow().id();
        requireSameFingerprint(existing.orElseThrow(), fingerprint);
        Customer customer = customers.findOrCreate(customerName, customerPhone, customerEmail);
        return replay(existing.orElseThrow(), context, customer);
      }

      if (!availability.isAvailable(
          context.tenantId(),
          context.calendarId(),
          context.durationMinutes(),
          context.zoneId(),
          command.startAt(),
          now)) {
        throw new BookingException(
            SLOT_UNAVAILABLE_VALIDATION, "requested start time is not available");
      }

      Customer customer = customers.findOrCreate(customerName, customerPhone, customerEmail);
      Appointment appointment =
          new Appointment(
              UUID.randomUUID(),
              context.tenantId(),
              context.calendarId(),
              context.offeringId(),
              customer.id(),
              command.startAt(),
              command.startAt().plus(Duration.ofMinutes(context.durationMinutes())),
              context.durationMinutes(),
              context.zoneId().getId(),
              Status.SCHEDULED,
              customerNotes,
              idempotencyKey,
              fingerprint);
      appointmentId = appointment.id();

      if (!appointments.insert(appointment)) {
        Appointment winner =
            appointments
                .findByTenantIdAndIdempotencyKey(context.tenantId(), idempotencyKey)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "idempotency conflict winner was not visible after insert"));
        appointmentId = winner.id();
        requireSameFingerprint(winner, fingerprint);
        return replay(winner, context, customer);
      }

      appointments.insertSlots(
          context.tenantId(),
          appointment.id(),
          context.calendarId(),
          slotGenerator.generate(command.startAt(), context.durationMinutes()));
      CreationResult result = new CreationResult(toResult(appointment, context, customer), false);
      logAfterCommit(
          "appointment.booking_succeeded",
          "appointment booking succeeded",
          context.tenantId(),
          context.calendarId(),
          appointment.id());
      return result;
    } catch (BookingException exception) {
      logFailure(exception, tenantId, calendarId, appointmentId);
      throw exception;
    }
  }

  private CreationResult replay(
      Appointment appointment, BookingContext context, Customer customer) {
    CreationResult result = new CreationResult(toResult(appointment, context, customer), true);
    logAfterCommit(
        "appointment.booking_replayed",
        "appointment booking replayed",
        context.tenantId(),
        context.calendarId(),
        appointment.id());
    return result;
  }

  private static void requireCommand(CreateAppointmentCommand command) {
    if (command == null
        || command.tenantSlug() == null
        || command.tenantSlug().isBlank()
        || command.calendarId() == null
        || command.offeringId() == null
        || command.startAt() == null) {
      throw new BookingException("validation_error", "booking request is incomplete");
    }
  }

  private static String normalizeIdempotencyKey(String value) {
    if (value == null || value.isBlank() || value.strip().length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
      throw new BookingException(
          "validation_error", "Idempotency-Key must be present and at most 255 characters");
    }
    return value.strip();
  }

  private static String normalizeCustomerName(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120) {
      throw new BookingException("validation_error", "customer_name is invalid");
    }
    return value.strip();
  }

  private static String normalizeOptional(String value, boolean lowercase) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      return null;
    }
    return lowercase ? normalized.toLowerCase(Locale.ROOT) : normalized;
  }

  private static void validateStart(Instant startAt, Instant now) {
    Objects.requireNonNull(now, "now");
    if (startAt.getNano() != 0 || Math.floorMod(startAt.getEpochSecond(), 60) != 0) {
      throw new BookingException(
          "validation_error", "start_at seconds and fractional seconds must be zero");
    }
    if (Math.floorMod(startAt.getEpochSecond(), Duration.ofMinutes(15).toSeconds()) != 0
        || !startAt.isAfter(now)) {
      throw new BookingException(
          SLOT_UNAVAILABLE_VALIDATION, "requested start time is not available");
    }
  }

  private static void requireSameFingerprint(Appointment existing, String fingerprint) {
    if (!existing.idempotencyFingerprint().equals(fingerprint)) {
      throw new BookingException(
          "idempotency_key_conflict", "Idempotency-Key was already used for another request");
    }
  }

  private static AppointmentResult toResult(
      Appointment appointment, BookingContext context, Customer customer) {
    return new AppointmentResult(
        appointment.id(),
        appointment.startAt(),
        appointment.endAt(),
        appointment.status().name().toLowerCase(Locale.ROOT),
        new AppointmentResult.CalendarSummary(
            context.calendarId(), context.calendarName(), context.zoneId().getId()),
        new AppointmentResult.OfferingSummary(
            context.offeringId(),
            context.offeringTitle(),
            appointment.durationMinutesSnapshot(),
            context.priceCents()),
        new AppointmentResult.CustomerSummary(
            customer.id(), customer.name(), customer.phone(), customer.email()),
        appointment.customerNotes());
  }

  private static void logAfterCommit(
      String eventName, String message, UUID tenantId, UUID calendarId, UUID appointmentId) {
    Runnable log = () -> logInfo(eventName, message, tenantId, calendarId, appointmentId);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              log.run();
            }
          });
    } else {
      log.run();
    }
  }

  private static void logFailure(
      BookingException exception, UUID tenantId, UUID calendarId, UUID appointmentId) {
    String eventName =
        switch (exception.code()) {
          case "slot_unavailable", "idempotency_key_conflict" -> "appointment.booking_conflict";
          default -> "appointment.booking_rejected";
        };
    LOGGER
        .atWarn()
        .addKeyValue("event_name", eventName)
        .addKeyValue("tenant.id", safeId(tenantId))
        .addKeyValue("calendar.id", safeId(calendarId))
        .addKeyValue("appointment.id", safeId(appointmentId))
        .addKeyValue("error.type", publicErrorCode(exception.code()))
        .log("appointment booking was not completed");
  }

  private static void logInfo(
      String eventName, String message, UUID tenantId, UUID calendarId, UUID appointmentId) {
    LOGGER
        .atInfo()
        .addKeyValue("event_name", eventName)
        .addKeyValue("tenant.id", safeId(tenantId))
        .addKeyValue("calendar.id", safeId(calendarId))
        .addKeyValue("appointment.id", safeId(appointmentId))
        .log(message);
  }

  private static String safeId(UUID id) {
    return id == null ? "unknown" : id.toString();
  }

  private static String publicErrorCode(String code) {
    return SLOT_UNAVAILABLE_VALIDATION.equals(code) ? "slot_unavailable" : code;
  }
}
