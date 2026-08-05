package io.gnomon.customers.application.port.out;

import io.gnomon.customers.application.port.in.CustomerPage;
import io.gnomon.customers.application.port.in.CustomerResult;
import java.util.Optional;
import java.util.UUID;

public interface AdminCustomerQueryPort {
  CustomerPage findPage(UUID tenantId, int page, int size);

  Optional<CustomerResult> findByTenantIdAndId(UUID tenantId, UUID id);

  boolean existsById(UUID id);
}
