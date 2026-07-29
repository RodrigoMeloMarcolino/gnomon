package io.gnomon.catalog.api.controller;

import io.gnomon.catalog.api.request.CreateOfferingRequest;
import io.gnomon.catalog.api.request.UpdateOfferingRequest;
import io.gnomon.catalog.api.response.OfferingResponse;
import io.gnomon.catalog.application.port.in.CreateOfferingCommand;
import io.gnomon.catalog.application.port.in.CreateOfferingUseCase;
import io.gnomon.catalog.application.port.in.DeactivateOfferingUseCase;
import io.gnomon.catalog.application.port.in.GetOfferingUseCase;
import io.gnomon.catalog.application.port.in.ListOfferingsUseCase;
import io.gnomon.catalog.application.port.in.UpdateOfferingCommand;
import io.gnomon.catalog.application.port.in.UpdateOfferingUseCase;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/offerings")
public class OfferingController {

  private final CreateOfferingUseCase createOffering;
  private final ListOfferingsUseCase listOfferings;
  private final GetOfferingUseCase getOffering;
  private final UpdateOfferingUseCase updateOffering;
  private final DeactivateOfferingUseCase deactivateOffering;

  public OfferingController(
      CreateOfferingUseCase createOffering,
      ListOfferingsUseCase listOfferings,
      GetOfferingUseCase getOffering,
      UpdateOfferingUseCase updateOffering,
      DeactivateOfferingUseCase deactivateOffering) {
    this.createOffering = createOffering;
    this.listOfferings = listOfferings;
    this.getOffering = getOffering;
    this.updateOffering = updateOffering;
    this.deactivateOffering = deactivateOffering;
  }

  @PostMapping
  ResponseEntity<OfferingResponse> create(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @Valid @RequestBody CreateOfferingRequest request) {
    var result =
        createOffering.create(
            new CreateOfferingCommand(
                principal.userId(),
                tenantSlug,
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.priceCents()));
    return ResponseEntity.status(HttpStatus.CREATED).body(OfferingResponse.from(result));
  }

  @GetMapping
  List<OfferingResponse> list(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug) {
    return listOfferings.list(principal.userId(), tenantSlug).stream()
        .map(OfferingResponse::from)
        .toList();
  }

  @GetMapping("/{offeringId}")
  OfferingResponse get(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @PathVariable UUID offeringId) {
    return OfferingResponse.from(getOffering.get(principal.userId(), tenantSlug, offeringId));
  }

  @PatchMapping("/{offeringId}")
  OfferingResponse update(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @PathVariable UUID offeringId,
      @Valid @RequestBody UpdateOfferingRequest request) {
    return OfferingResponse.from(
        updateOffering.update(
            new UpdateOfferingCommand(
                principal.userId(),
                tenantSlug,
                offeringId,
                request.titleChange(),
                request.descriptionChange(),
                request.durationMinutesChange(),
                request.priceCentsChange(),
                request.activeChange())));
  }

  @DeleteMapping("/{offeringId}")
  ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @PathVariable UUID offeringId) {
    deactivateOffering.deactivate(principal.userId(), tenantSlug, offeringId);
    return ResponseEntity.noContent().build();
  }
}
