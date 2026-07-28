package io.gnomon.availability.api;

import io.gnomon.availability.application.ListAvailableSlotsUseCase;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/public/tenants/{tenantSlug}")
public class PublicAvailabilityController {

  private final ListAvailableSlotsUseCase listAvailableSlots;

  public PublicAvailabilityController(ListAvailableSlotsUseCase listAvailableSlots) {
    this.listAvailableSlots = listAvailableSlots;
  }

  @GetMapping("/available-slots")
  AvailableSlotsResponse list(
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @RequestParam("calendar_id") UUID calendarId,
      @RequestParam("offering_id") UUID offeringId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return new AvailableSlotsResponse(
        listAvailableSlots.list(tenantSlug, calendarId, offeringId, date));
  }
}
