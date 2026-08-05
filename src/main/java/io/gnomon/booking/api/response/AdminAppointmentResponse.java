package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.AdminAppointment;
import io.gnomon.booking.application.port.in.CalendarSummary;
import io.gnomon.booking.application.port.in.CustomerSummary;
import io.gnomon.booking.application.port.in.OfferingSummary;
import java.time.Instant;
import java.util.UUID;

public record AdminAppointmentResponse(
    UUID id,
    Instant startAt,
    Instant endAt,
    String status,
    CalendarSummary calendar,
    OfferingSummary offering,
    CustomerSummary customer,
    String customerNotes) {
  public static AdminAppointmentResponse from(AdminAppointment value) {
    return new AdminAppointmentResponse(
        value.id(),
        value.startAt(),
        value.endAt(),
        value.status(),
        value.calendar(),
        value.offering(),
        value.customer(),
        value.customerNotes());
  }

  public static AdminAppointmentResponse summaryFrom(AdminAppointment value) {
    return new AdminAppointmentResponse(
        value.id(),
        value.startAt(),
        value.endAt(),
        value.status(),
        value.calendar(),
        value.offering(),
        new CustomerSummary(value.customer().id(), value.customer().name(), null, null),
        null);
  }
}
