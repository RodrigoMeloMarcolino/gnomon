package io.gnomon.tenancy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank
        @Size(max = 80)
        @Pattern(
            regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
            message = "must contain lowercase letters, numbers, and single hyphens only")
        String slug,
    @NotBlank @Size(max = 64) String timezone) {}
