package io.gnomon.tenancy.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMembershipRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank
        @Pattern(
            regexp = "owner|admin",
            message = "must be owner or admin; staff is created by linking a collaborator")
        String role) {}
