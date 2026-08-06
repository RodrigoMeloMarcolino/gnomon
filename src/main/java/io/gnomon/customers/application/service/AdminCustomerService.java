package io.gnomon.customers.application.service;

import io.gnomon.customers.application.port.in.AdminCustomerUseCase;
import io.gnomon.customers.application.port.in.CustomerPage;
import io.gnomon.customers.application.port.in.CustomerResult;
import io.gnomon.customers.application.port.out.AdminCustomerQueryPort;
import io.gnomon.customers.application.port.out.CustomerTenantAccessPort;
import io.gnomon.customers.domain.exception.CustomerException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCustomerService implements AdminCustomerUseCase {
  private final CustomerTenantAccessPort tenants;
  private final AdminCustomerQueryPort customers;

  public AdminCustomerService(CustomerTenantAccessPort tenants, AdminCustomerQueryPort customers) {
    this.tenants = tenants;
    this.customers = customers;
  }

  @Override
  public CustomerPage list(UUID user, String slug, int page, int size) {
    var tenant = tenants.requireManager(user, slug);
    validate(page, size);
    return customers.findPage(tenant.tenantId(), page, size);
  }

  @Override
  public CustomerResult get(UUID user, String slug, UUID id) {
    var tenant = tenants.requireManager(user, slug);
    return customers.findByTenantIdAndId(tenant.tenantId(), id).orElseGet(() -> absent(id));
  }

  private CustomerResult absent(UUID id) {
    if (customers.existsById(id))
      throw new CustomerException(
          "customer_access_denied", "customer has no appointment in this tenant");
    throw new CustomerException("customer_not_found", "customer was not found");
  }

  private static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new CustomerException(
          "validation_error", "page must be non-negative and size between 1 and 100");
  }
}
