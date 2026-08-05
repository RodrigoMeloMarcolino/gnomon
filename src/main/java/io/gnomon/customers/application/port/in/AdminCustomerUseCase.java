package io.gnomon.customers.application.port.in;

import java.util.UUID;

public interface AdminCustomerUseCase {
  CustomerPage list(UUID actorUserId, String tenantSlug, int page, int size);

  CustomerResult get(UUID actorUserId, String tenantSlug, UUID id);
}
