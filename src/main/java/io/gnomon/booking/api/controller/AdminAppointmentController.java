package io.gnomon.booking.api.controller;

import io.gnomon.booking.api.response.AdminAppointmentResponse;
import io.gnomon.booking.api.response.PageResponse;
import io.gnomon.booking.application.port.in.AdminAppointmentQueryUseCase;
import io.gnomon.booking.application.port.in.AdminAppointmentTransitionUseCase;
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
  private final AdminAppointmentQueryUseCase queries;
  private final AdminAppointmentTransitionUseCase transitions;

  public AdminAppointmentController(
      AdminAppointmentQueryUseCase queries, AdminAppointmentTransitionUseCase transitions) {
    this.queries = queries;
    this.transitions = transitions;
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
        queries.list(
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
    return AdminAppointmentResponse.from(queries.get(user.userId(), tenantSlug, id));
  }

  @PostMapping("/{id}/cancel")
  AdminAppointmentResponse cancel(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentTransitionUseCase.Transition.CANCEL);
  }

  @PostMapping("/{id}/complete")
  AdminAppointmentResponse complete(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentTransitionUseCase.Transition.COMPLETE);
  }

  @PostMapping("/{id}/no-show")
  AdminAppointmentResponse noShow(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return transition(user, tenantSlug, id, AdminAppointmentTransitionUseCase.Transition.NO_SHOW);
  }

  private AdminAppointmentResponse transition(
      LocalUserPrincipal user,
      String slug,
      UUID id,
      AdminAppointmentTransitionUseCase.Transition transition) {
    return AdminAppointmentResponse.from(
        transitions.transition(user.userId(), slug, id, transition));
  }
}
