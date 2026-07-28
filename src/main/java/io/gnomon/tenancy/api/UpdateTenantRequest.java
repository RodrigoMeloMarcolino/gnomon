package io.gnomon.tenancy.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
    @Size(min = 1, max = 120) String name,
    @Size(min = 1, max = 64) String timezone,
    @Pattern(regexp = "active|suspended", message = "must be active or suspended") String status) {}
