package io.gnomon.tenancy.application.port.in.result;

import java.util.UUID;

public record TenantAccessResult(UUID tenantId, String role) {}
