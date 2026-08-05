package io.gnomon.booking.application.port.out;

import java.util.UUID;

public record AdminTenantAccess(UUID tenantId, String role) {}
