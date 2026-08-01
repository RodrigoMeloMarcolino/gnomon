package io.gnomon.tenancy.api.controller;

import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import io.gnomon.tenancy.api.request.CreateTenantRequest;
import io.gnomon.tenancy.api.request.UpdateTenantRequest;
import io.gnomon.tenancy.api.response.TenantResponse;
import io.gnomon.tenancy.api.response.TenantSelectionResponse;
import io.gnomon.tenancy.application.port.in.CreateTenantCommand;
import io.gnomon.tenancy.application.port.in.CreateTenantUseCase;
import io.gnomon.tenancy.application.port.in.GetTenantUseCase;
import io.gnomon.tenancy.application.port.in.ListMyTenantsUseCase;
import io.gnomon.tenancy.application.port.in.UpdateTenantCommand;
import io.gnomon.tenancy.application.port.in.UpdateTenantUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/tenants")
public class TenantController {

  private final CreateTenantUseCase createTenant;
  private final ListMyTenantsUseCase listMyTenants;
  private final GetTenantUseCase getTenant;
  private final UpdateTenantUseCase updateTenant;

  public TenantController(
      CreateTenantUseCase createTenant,
      ListMyTenantsUseCase listMyTenants,
      GetTenantUseCase getTenant,
      UpdateTenantUseCase updateTenant) {
    this.createTenant = createTenant;
    this.listMyTenants = listMyTenants;
    this.getTenant = getTenant;
    this.updateTenant = updateTenant;
  }

  @PostMapping
  ResponseEntity<TenantResponse> create(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @Valid @RequestBody CreateTenantRequest request) {
    var result =
        createTenant.create(
            new CreateTenantCommand(
                principal.userId(),
                request.name(),
                request.slug(),
                request.timezone(),
                request.currencyCode()));
    return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(result));
  }

  @GetMapping
  List<TenantSelectionResponse> list(@AuthenticationPrincipal LocalUserPrincipal principal) {
    return listMyTenants.list(principal.userId()).stream()
        .map(TenantSelectionResponse::from)
        .toList();
  }

  @GetMapping("/{tenantSlug}")
  TenantResponse get(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug) {
    return TenantResponse.from(getTenant.get(principal.userId(), tenantSlug));
  }

  @PatchMapping("/{tenantSlug}")
  TenantResponse update(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug,
      @Valid @RequestBody UpdateTenantRequest request) {
    return TenantResponse.from(
        updateTenant.update(
            new UpdateTenantCommand(
                principal.userId(),
                tenantSlug,
                request.name(),
                request.timezone(),
                request.currencyCode(),
                request.status())));
  }
}
