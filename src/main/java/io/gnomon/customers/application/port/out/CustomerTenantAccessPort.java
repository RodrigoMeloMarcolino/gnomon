package io.gnomon.customers.application.port.out;

import java.util.UUID;

public interface CustomerTenantAccessPort {
  CustomerTenantAccess requireManager(UUID actorUserId, String tenantSlug);
}
