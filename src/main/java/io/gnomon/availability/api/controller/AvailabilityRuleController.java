package io.gnomon.availability.api.controller;

import io.gnomon.availability.api.request.CreateAvailabilityRuleRequest;
import io.gnomon.availability.api.request.UpdateAvailabilityRuleRequest;
import io.gnomon.availability.api.response.AvailabilityRuleResponse;
import io.gnomon.availability.application.port.in.AvailabilityRuleUseCase;
import io.gnomon.availability.application.port.in.CreateAvailabilityRuleCommand;
import io.gnomon.availability.application.port.in.UpdateAvailabilityRuleCommand;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/calendars/{calendarId}/availability-rules")
public class AvailabilityRuleController {

  private final AvailabilityRuleUseCase rules;

  public AvailabilityRuleController(AvailabilityRuleUseCase rules) {
    this.rules = rules;
  }

  @PostMapping
  ResponseEntity<AvailabilityRuleResponse> create(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId,
      @Valid @RequestBody CreateAvailabilityRuleRequest request) {
    var result =
        rules.create(
            new CreateAvailabilityRuleCommand(
                principal.userId(),
                tenantSlug,
                calendarId,
                request.weekday(),
                request.startTime(),
                request.endTime()));
    return ResponseEntity.status(HttpStatus.CREATED).body(AvailabilityRuleResponse.from(result));
  }

  @GetMapping
  List<AvailabilityRuleResponse> list(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId) {
    return rules.list(principal.userId(), tenantSlug, calendarId).stream()
        .map(AvailabilityRuleResponse::from)
        .toList();
  }

  @GetMapping("/{ruleId}")
  AvailabilityRuleResponse get(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId,
      @PathVariable UUID ruleId) {
    return AvailabilityRuleResponse.from(
        rules.get(principal.userId(), tenantSlug, calendarId, ruleId));
  }

  @PatchMapping("/{ruleId}")
  AvailabilityRuleResponse update(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId,
      @PathVariable UUID ruleId,
      @Valid @RequestBody UpdateAvailabilityRuleRequest request) {
    return AvailabilityRuleResponse.from(
        rules.update(
            new UpdateAvailabilityRuleCommand(
                principal.userId(),
                tenantSlug,
                calendarId,
                ruleId,
                request.weekday(),
                request.startTime(),
                request.endTime(),
                request.active())));
  }

  @DeleteMapping("/{ruleId}")
  ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID calendarId,
      @PathVariable UUID ruleId) {
    rules.deactivate(principal.userId(), tenantSlug, calendarId, ruleId);
    return ResponseEntity.noContent().build();
  }
}
