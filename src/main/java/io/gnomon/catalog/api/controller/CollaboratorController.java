package io.gnomon.catalog.api.controller;

import io.gnomon.catalog.api.request.CollaboratorRequest;
import io.gnomon.catalog.api.request.LinkCollaboratorUserRequest;
import io.gnomon.catalog.api.request.UpdateCollaboratorRequest;
import io.gnomon.catalog.api.response.CollaboratorResponse;
import io.gnomon.catalog.application.port.in.CollaboratorUseCase;
import io.gnomon.catalog.application.port.in.CollaboratorUseCase.CreateCollaboratorCommand;
import io.gnomon.catalog.application.port.in.CollaboratorUseCase.LinkCollaboratorUserCommand;
import io.gnomon.catalog.application.port.in.CollaboratorUseCase.UpdateCollaboratorCommand;
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
@RequestMapping("/v1/tenants/{tenantSlug}/collaborators")
public class CollaboratorController {

  private final CollaboratorUseCase collaborators;

  public CollaboratorController(CollaboratorUseCase collaborators) {
    this.collaborators = collaborators;
  }

  @PostMapping
  ResponseEntity<CollaboratorResponse> create(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @Valid @RequestBody CollaboratorRequest request) {
    var result =
        collaborators.create(
            new CreateCollaboratorCommand(principal.userId(), tenantSlug, request.displayName()));
    return ResponseEntity.status(HttpStatus.CREATED).body(CollaboratorResponse.from(result));
  }

  @GetMapping
  List<CollaboratorResponse> list(
      @AuthenticationPrincipal LocalUserPrincipal principal, @PathVariable String tenantSlug) {
    return collaborators.list(principal.userId(), tenantSlug).stream()
        .map(CollaboratorResponse::from)
        .toList();
  }

  @GetMapping("/{collaboratorId}")
  CollaboratorResponse get(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID collaboratorId) {
    return CollaboratorResponse.from(
        collaborators.get(principal.userId(), tenantSlug, collaboratorId));
  }

  @PatchMapping("/{collaboratorId}")
  CollaboratorResponse update(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID collaboratorId,
      @Valid @RequestBody UpdateCollaboratorRequest request) {
    return CollaboratorResponse.from(
        collaborators.update(
            new UpdateCollaboratorCommand(
                principal.userId(), tenantSlug, collaboratorId, request.displayName())));
  }

  @DeleteMapping("/{collaboratorId}")
  ResponseEntity<Void> deactivate(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID collaboratorId) {
    collaborators.deactivate(principal.userId(), tenantSlug, collaboratorId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{collaboratorId}/user-link")
  CollaboratorResponse link(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID collaboratorId,
      @Valid @RequestBody LinkCollaboratorUserRequest request) {
    return CollaboratorResponse.from(
        collaborators.linkUser(
            new LinkCollaboratorUserCommand(
                principal.userId(), tenantSlug, collaboratorId, request.email())));
  }

  @DeleteMapping("/{collaboratorId}/user-link")
  CollaboratorResponse unlink(
      @AuthenticationPrincipal LocalUserPrincipal principal,
      @PathVariable String tenantSlug,
      @PathVariable UUID collaboratorId) {
    return CollaboratorResponse.from(
        collaborators.unlinkUser(principal.userId(), tenantSlug, collaboratorId));
  }
}
