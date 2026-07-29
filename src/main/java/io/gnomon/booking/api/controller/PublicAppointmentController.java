package io.gnomon.booking.api.controller;

import io.gnomon.booking.api.request.CreateAppointmentRequest;
import io.gnomon.booking.api.response.AppointmentResponse;
import io.gnomon.booking.application.port.in.CreateAppointmentCommand;
import io.gnomon.booking.application.port.in.CreateAppointmentUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

  private final CreateAppointmentUseCase createAppointment;

  public PublicAppointmentController(CreateAppointmentUseCase createAppointment) {
    this.createAppointment = createAppointment;
  }

  @PostMapping
  ResponseEntity<AppointmentResponse> create(
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
      @Valid @RequestBody CreateAppointmentRequest request) {
    var result =
        createAppointment.create(
            new CreateAppointmentCommand(
                tenantSlug,
                idempotencyKey,
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
