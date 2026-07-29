package io.gnomon.tenancy.api.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
    @Size(min = 1, max = 120) String name,
    @Size(min = 1, max = 64) String timezone,
    @Pattern(regexp = "^[A-Z]{3}$", message = "must be a three-letter uppercase ISO 4217 code")
        String currencyCode,
    @Pattern(regexp = "active|suspended", message = "must be active or suspended") String status) {}
