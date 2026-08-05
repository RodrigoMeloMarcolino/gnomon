package io.gnomon.customers.application.service;

import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.customers.application.port.in.AdminCustomerUseCase;
import io.gnomon.customers.application.port.in.CustomerPage;
import io.gnomon.customers.application.port.in.CustomerResult;
import io.gnomon.customers.application.port.out.AdminCustomerQueryPort;
import io.gnomon.customers.domain.exception.CustomerException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminCustomerService implements AdminCustomerUseCase {
  private final CatalogTenantAccessPort tenants;
  private final AdminCustomerQueryPort customers;

  public AdminCustomerService(CatalogTenantAccessPort tenants, AdminCustomerQueryPort customers) {
    this.tenants = tenants;
    this.customers = customers;
  }

  @Override
  public CustomerPage list(UUID user, String slug, int page, int size) {
    var tenant = manager(user, slug);
    validate(page, size);
    return customers.findPage(tenant.tenantId(), page, size);
  }

  @Override
  public CustomerResult get(UUID user, String slug, UUID id) {
    var tenant = manager(user, slug);
    return customers.findByTenantIdAndId(tenant.tenantId(), id).orElseGet(() -> absent(id));
  }

  private io.gnomon.catalog.application.port.out.TenantAccess manager(UUID user, String slug) {
    try {
      return tenants.requireManager(user, slug);
    } catch (CatalogException ex) {
      throw new CustomerException(ex.code(), ex.getMessage());
    }
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
