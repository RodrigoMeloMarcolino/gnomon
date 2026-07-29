package io.gnomon.tenancy.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateMembershipRequest(
    @NotBlank @Pattern(regexp = "owner|admin|staff", message = "must be owner, admin, or staff")
        String role) {}
