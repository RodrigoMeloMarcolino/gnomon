package io.gnomon.booking.application.service;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.in.AdminAppointment;
import io.gnomon.booking.application.port.in.AdminAppointmentTransitionUseCase;
import io.gnomon.booking.application.port.out.AdminAppointmentQueryPort;
import io.gnomon.booking.application.port.out.AdminTenantAccessPort;
import io.gnomon.booking.application.port.out.AppointmentRepository;
import io.gnomon.booking.application.port.out.StaffCalendarAccessPort;
import io.gnomon.booking.domain.exception.BookingDomainException;
import io.gnomon.booking.domain.model.Appointment;
import io.gnomon.shared.logging.StructuredEventLogger;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdminAppointmentTransitionService implements AdminAppointmentTransitionUseCase {
  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(AdminAppointmentTransitionService.class);

  private final AdminTenantAccessPort tenants;
  private final StaffCalendarAccessPort staffCalendars;
  private final AdminAppointmentQueryPort queries;
  private final AppointmentRepository appointments;

  public AdminAppointmentTransitionService(
      AdminTenantAccessPort tenants,
      StaffCalendarAccessPort staffCalendars,
      AdminAppointmentQueryPort queries,
      AppointmentRepository appointments) {
    this.tenants = tenants;
    this.staffCalendars = staffCalendars;
    this.queries = queries;
    this.appointments = appointments;
  }

  @Override
  @Transactional
  public AdminAppointment transition(UUID userId, String slug, UUID id, Transition transition) {
    var access = tenants.requireMember(userId, slug);
    Appointment current =
        appointments
            .findByTenantIdAndIdForUpdate(access.tenantId(), id)
            .orElseThrow(() -> absentAppointment(id));
    authorizeCalendar(access.tenantId(), access.role(), userId, current.calendarId());

    Appointment changed;
    try {
      changed =
          switch (transition) {
            case CANCEL -> current.cancel();
            case COMPLETE -> current.complete();
            case NO_SHOW -> current.markNoShow();
          };
    } catch (BookingDomainException exception) {
      throw new BookingException(exception.code(), exception.getMessage());
    }
    appointments.updateStatus(access.tenantId(), id, changed.status());
    if (transition == Transition.CANCEL) appointments.deleteSlots(access.tenantId(), id);
    registerEvent(transition, id, access.tenantId(), current.calendarId());
    return queries.findByTenantIdAndId(access.tenantId(), id).orElseThrow();
  }

  private void registerEvent(Transition transition, UUID id, UUID tenantId, UUID calendarId) {
    String event =
        switch (transition) {
          case CANCEL -> "appointment.cancelled";
          case COMPLETE -> "appointment.completed";
          case NO_SHOW -> "appointment.no_show";
        };
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            LOGGER.info(
                event,
                "appointment status changed",
                Map.of("appointment_id", id, "tenant_id", tenantId, "calendar_id", calendarId));
          }
        });
  }

  private UUID authorizeCalendar(UUID tenantId, String role, UUID userId, UUID requested) {
    if (!"staff".equals(role)) return requested;
    UUID own =
        staffCalendars
            .findCalendarIdForStaff(tenantId, userId)
            .orElseThrow(
                () ->
                    new BookingException(
                        "insufficient_role", "staff must be linked to a calendar"));
    if (!requested.equals(own))
      throw new BookingException(
          "staff_calendar_mismatch", "staff can only access their own calendar");
    return own;
  }

  private RuntimeException absentAppointment(UUID id) {
    if (appointments.existsById(id))
      throw new BookingException(
          "appointment_access_denied", "appointment belongs to another tenant");
    throw new BookingException("appointment_not_found", "appointment was not found");
  }
}
