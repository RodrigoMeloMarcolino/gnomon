package io.gnomon.booking.application.port.out;

import java.util.UUID;

public interface AdminTenantAccessPort {
  AdminTenantAccess requireMember(UUID actorUserId, String tenantSlug);
}
