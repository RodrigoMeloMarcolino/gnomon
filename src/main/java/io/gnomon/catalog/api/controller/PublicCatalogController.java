package io.gnomon.catalog.api.controller;

import io.gnomon.catalog.api.response.PublicOfferingResponse;
import io.gnomon.catalog.api.response.PublicTenantProfileResponse;
import io.gnomon.catalog.application.port.in.GetPublicTenantProfileUseCase;
import io.gnomon.catalog.application.port.in.ListPublicOfferingsUseCase;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/public/tenants/{tenantSlug}")
public class PublicCatalogController {

  private final GetPublicTenantProfileUseCase getPublicTenantProfile;
  private final ListPublicOfferingsUseCase listPublicOfferings;

  public PublicCatalogController(
      GetPublicTenantProfileUseCase getPublicTenantProfile,
      ListPublicOfferingsUseCase listPublicOfferings) {
    this.getPublicTenantProfile = getPublicTenantProfile;
    this.listPublicOfferings = listPublicOfferings;
  }

  @GetMapping
  PublicTenantProfileResponse getProfile(
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug) {
    return PublicTenantProfileResponse.from(getPublicTenantProfile.get(tenantSlug));
  }

  @GetMapping("/offerings")
  List<PublicOfferingResponse> listOfferings(
      @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String tenantSlug,
      @RequestParam(name = "calendar_id", required = false) UUID calendarId) {
    return listPublicOfferings.list(tenantSlug, calendarId).stream()
        .map(PublicOfferingResponse::from)
        .toList();
  }
}
