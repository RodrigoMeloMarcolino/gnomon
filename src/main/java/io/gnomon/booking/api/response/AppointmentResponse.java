package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.AppointmentResult;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AppointmentResponse(
    UUID id,
    Instant startAt,
    Instant endAt,
    String status,
    CalendarResponse calendar,
    OfferingResponse offering,
    CustomerResponse customer,
    String customerNotes) {

  public static AppointmentResponse from(AppointmentResult result) {
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
}
