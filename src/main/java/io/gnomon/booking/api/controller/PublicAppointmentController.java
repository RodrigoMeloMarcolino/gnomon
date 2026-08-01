package io.gnomon.booking.api.controller;

import io.gnomon.booking.api.request.CreateAppointmentRequest;
import io.gnomon.booking.api.response.AppointmentResponse;
import io.gnomon.booking.application.exception.BookingException;
import io.gnomon.booking.application.port.in.CreateAppointmentCommand;
import io.gnomon.booking.application.port.in.CreateAppointmentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/public/tenants/{tenantSlug}/appointments")
public class PublicAppointmentController {

  private static final String CANONICAL_UUID =
      "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

  private final CreateAppointmentUseCase createAppointment;

  public PublicAppointmentController(CreateAppointmentUseCase createAppointment) {
    this.createAppointment = createAppointment;
  }

  @PostMapping
  ResponseEntity<AppointmentResponse> create(
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @RequestHeader("Idempotency-Key")
          @NotBlank
          @Pattern(regexp = CANONICAL_UUID, message = "must be a canonical UUID")
          String idempotencyKey,
      @Valid @RequestBody CreateAppointmentRequest request) {
    if (!idempotencyKey.matches(CANONICAL_UUID)) {
      throw new BookingException("validation_error", "Idempotency-Key must be a canonical UUID");
    }
    var result =
        createAppointment.create(
            new CreateAppointmentCommand(
                tenantSlug,
                idempotencyKey.toLowerCase(java.util.Locale.ROOT),
                request.calendarId(),
                request.offeringId(),
                request.startAt().toInstant(),
                request.customerName(),
                request.customerPhone(),
                request.customerEmail(),
                request.customerNotes()));
    HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(AppointmentResponse.from(result.appointment()));
  }
}
