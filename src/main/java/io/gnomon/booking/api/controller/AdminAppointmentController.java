package io.gnomon.booking.api.controller;

import io.gnomon.booking.api.response.AdminAppointmentResponse;
import io.gnomon.booking.api.response.PageResponse;
import io.gnomon.booking.application.port.in.AdminAppointmentUseCase;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/appointments")
public class AdminAppointmentController {
  private final AdminAppointmentUseCase appointments;

  public AdminAppointmentController(AdminAppointmentUseCase appointments) {
    this.appointments = appointments;
  }

  @GetMapping
  PageResponse<AdminAppointmentResponse> list(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @RequestParam(name = "calendar_id", required = false) UUID calendarId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        appointments.list(
            user.userId(),
            tenantSlug,
            from.toInstant(),
            to.toInstant(),
            calendarId,
            status,
            page,
            size));
  }

  @GetMapping("/{id}")
  AdminAppointmentResponse get(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return AdminAppointmentResponse.from(appointments.get(user.userId(), tenantSlug, id));
  }

  @PostMapping("/{id}/cancel")
  AdminAppointmentResponse cancel(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentUseCase.Transition.CANCEL);
  }

  @PostMapping("/{id}/complete")
  AdminAppointmentResponse complete(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentUseCase.Transition.COMPLETE);
  }

  @PostMapping("/{id}/no-show")
  AdminAppointmentResponse noShow(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentUseCase.Transition.NO_SHOW);
  }

  private AdminAppointmentResponse transition(
      LocalUserPrincipal user,
      String slug,
      UUID id,
      AdminAppointmentUseCase.Transition transition) {
    return AdminAppointmentResponse.from(
        appointments.transition(user.userId(), slug, id, transition));
  }
}
