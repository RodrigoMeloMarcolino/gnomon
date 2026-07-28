package io.gnomon.catalog.api;

import io.gnomon.catalog.application.ReplaceCalendarOfferingsUseCase;
import io.gnomon.catalog.application.ReplaceCalendarOfferingsUseCase.ReplaceCalendarOfferingsCommand;
import io.gnomon.tenancy.api.security.LocalUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/calendars/{calendarId}/offerings")
public class CalendarOfferingController {

  private final ReplaceCalendarOfferingsUseCase replaceCalendarOfferings;

  public CalendarOfferingController(ReplaceCalendarOfferingsUseCase replaceCalendarOfferings) {
    this.replaceCalendarOfferings = replaceCalendarOfferings;
  }

  @PutMapping
  List<OfferingResponse> replace(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @PathVariable UUID calendarId,
      @Valid @RequestBody ReplaceCalendarOfferingsRequest request) {
    return replaceCalendarOfferings
        .replace(
            new ReplaceCalendarOfferingsCommand(
                principal.userId(), tenantSlug, calendarId, request.offeringIds()))
        .stream()
        .map(OfferingResponse::from)
        .toList();
  }
}
