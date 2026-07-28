package io.gnomon.catalog.api;

import io.gnomon.catalog.application.CalendarUseCase;
import io.gnomon.catalog.application.CalendarUseCase.UpdateCalendarCommand;
import io.gnomon.tenancy.api.security.LocalUserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/calendars")
public class CalendarController {

  private final CalendarUseCase calendars;

  public CalendarController(CalendarUseCase calendars) {
    this.calendars = calendars;
  }

  @GetMapping("/{calendarId}")
  CalendarResponse get(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId) {
    return CalendarResponse.from(calendars.get(principal.userId(), tenantSlug, calendarId));
  }

  @PatchMapping("/{calendarId}")
  CalendarResponse update(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId,
      @Valid @RequestBody UpdateCalendarRequest request) {
    return CalendarResponse.from(
        calendars.update(
            new UpdateCalendarCommand(
                principal.userId(),
                tenantSlug,
                calendarId,
                request.name(),
                request.timezone(),
                request.active())));
  }

  @DeleteMapping("/{calendarId}")
  ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId) {
    calendars.deactivate(principal.userId(), tenantSlug, calendarId);
    return ResponseEntity.noContent().build();
  }
}
