package io.gnomon.booking.application.service;

import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.in.AdminAppointment;
import io.gnomon.booking.application.port.in.AdminAppointmentPage;
import io.gnomon.booking.application.port.in.AdminAppointmentQueryUseCase;
import io.gnomon.booking.application.port.out.AdminAppointmentQueryPort;
import io.gnomon.booking.application.port.out.AdminTenantAccessPort;
import io.gnomon.booking.application.port.out.StaffCalendarAccessPort;
import io.gnomon.booking.domain.model.Appointment;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminAppointmentQueryService implements AdminAppointmentQueryUseCase {
  private final AdminTenantAccessPort tenants;
  private final StaffCalendarAccessPort staffCalendars;
  private final AdminAppointmentQueryPort queries;

  public AdminAppointmentQueryService(
      AdminTenantAccessPort tenants,
      StaffCalendarAccessPort staffCalendars,
      AdminAppointmentQueryPort queries) {
    this.tenants = tenants;
    this.staffCalendars = staffCalendars;
    this.queries = queries;
  }

  @Override
  public AdminAppointmentPage list(
      UUID userId,
      String slug,
      Instant from,
      Instant to,
      UUID calendarId,
      String status,
      int page,
      int size) {
    validateRange(from, to);
    validatePage(page, size);
    String normalizedStatus = validateStatus(status);
    var access = tenants.requireMember(userId, slug);
    UUID effectiveCalendar = authorizeCalendar(access.tenantId(), access.role(), userId, calendarId);
    return queries.findPage(
        access.tenantId(), from, to, effectiveCalendar, normalizedStatus, page, size);
  }

  @Override
  public AdminAppointment get(UUID userId, String slug, UUID id) {
    var access = tenants.requireMember(userId, slug);
    AdminAppointment value =
        queries.findByTenantIdAndId(access.tenantId(), id).orElseThrow(() -> absentAppointment(id));
    authorizeCalendar(access.tenantId(), access.role(), userId, value.calendarId());
    return value;
  }

  private UUID authorizeCalendar(UUID tenantId, String role, UUID userId, UUID requested) {
    if (!"staff".equals(role)) return requested;
    UUID own =
        staffCalendars
            .findCalendarIdForStaff(tenantId, userId)
            .orElseThrow(
                () -> new BookingException("insufficient_role", "staff must be linked to a calendar"));
    if (requested != null && !requested.equals(own))
      throw new BookingException(
          "staff_calendar_mismatch", "staff can only access their own calendar");
    return own;
  }

  private RuntimeException absentAppointment(UUID id) {
    if (queries.existsById(id))
      throw new BookingException(
          "appointment_access_denied", "appointment belongs to another tenant");
    throw new BookingException("appointment_not_found", "appointment was not found");
  }

  private static void validateRange(Instant from, Instant to) {
    if (!from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0)
      throw new BookingException(
          "validation_error", "from/to must be a positive interval no longer than 31 days");
  }

  private static void validatePage(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new BookingException(
          "validation_error", "page must be non-negative and size between 1 and 100");
  }

  private static String validateStatus(String status) {
    if (status == null) return null;
    try {
      return Appointment.Status.valueOf(status.toUpperCase(java.util.Locale.ROOT))
          .name()
          .toLowerCase(java.util.Locale.ROOT);
    } catch (IllegalArgumentException exception) {
      throw new BookingException("validation_error", "status is invalid");
    }
  }
}
