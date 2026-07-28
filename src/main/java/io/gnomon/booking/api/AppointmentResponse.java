package io.gnomon.booking.api;

import io.gnomon.booking.application.AppointmentResult;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record AppointmentResponse(
    UUID id,
    Instant startAt,
    Instant endAt,
    String status,
    CalendarResponse calendar,
    OfferingResponse offering,
    CustomerResponse customer,
    String customerNotes) {

  static AppointmentResponse from(AppointmentResult result) {
    return new AppointmentResponse(
        result.id(),
        result.startAt(),
        result.endAt(),
        result.status(),
        CalendarResponse.from(result.calendar()),
        OfferingResponse.from(result.offering()),
        CustomerResponse.from(result.customer()),
        result.customerNotes());
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record CalendarResponse(UUID id, String name, String timezone) {

    static CalendarResponse from(AppointmentResult.CalendarSummary calendar) {
      return new CalendarResponse(calendar.id(), calendar.name(), calendar.timezone());
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record OfferingResponse(UUID id, String title, int durationMinutes, Integer priceCents) {

    static OfferingResponse from(AppointmentResult.OfferingSummary offering) {
      return new OfferingResponse(
          offering.id(), offering.title(), offering.durationMinutes(), offering.priceCents());
    }
  }

  @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
  record CustomerResponse(UUID id, String name, String phone, String email) {

    static CustomerResponse from(AppointmentResult.CustomerSummary customer) {
      return new CustomerResponse(
          customer.id(), customer.name(), customer.phone(), customer.email());
    }
  }
}
