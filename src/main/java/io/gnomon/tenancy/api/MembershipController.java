package io.gnomon.tenancy.api;

import io.gnomon.tenancy.api.security.LocalUserPrincipal;
import io.gnomon.tenancy.application.ManageMembershipUseCase;
import io.gnomon.tenancy.application.ManageMembershipUseCase.AddMembershipCommand;
import io.gnomon.tenancy.application.ManageMembershipUseCase.ChangeMembershipRoleCommand;
import io.gnomon.tenancy.application.ManageMembershipUseCase.RemoveMembershipCommand;
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
@RequestMapping("/v1/tenants/{tenantSlug}/memberships")
public class MembershipController {

  private final ManageMembershipUseCase manageMembership;

  public MembershipController(ManageMembershipUseCase manageMembership) {
    this.manageMembership = manageMembership;
  }

  @GetMapping
  List<MembershipResponse> list(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug) {
    return manageMembership.list(principal.userId(), tenantSlug).stream()
        .map(MembershipResponse::from)
        .toList();
  }

  @PostMapping
  ResponseEntity<MembershipResponse> add(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug,
      @Valid @RequestBody CreateMembershipRequest request) {
    var result =
        manageMembership.add(
            new AddMembershipCommand(
                principal.userId(), tenantSlug, request.email(), request.role()));
    return ResponseEntity.status(HttpStatus.CREATED).body(MembershipResponse.from(result));
  }

  @PatchMapping("/{membershipId}")
  MembershipResponse changeRole(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug,
      @PathVariable UUID membershipId,
      @Valid @RequestBody UpdateMembershipRequest request) {
    return MembershipResponse.from(
        manageMembership.changeRole(
            new ChangeMembershipRoleCommand(
                principal.userId(), tenantSlug, membershipId, request.role())));
  }

  @DeleteMapping("/{membershipId}")
  ResponseEntity<Void> remove(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable
          @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "must be a valid tenant slug")
          String tenantSlug,
      @PathVariable UUID membershipId) {
    manageMembership.remove(
        new RemoveMembershipCommand(principal.userId(), tenantSlug, membershipId));
    return ResponseEntity.noContent().build();
  }
}
