package io.gnomon.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMembershipRequest(
    @NotBlank
        @Pattern(
            regexp = "owner|admin",
            message = "must be owner or admin; staff is managed through collaborators")
        String role) {}
