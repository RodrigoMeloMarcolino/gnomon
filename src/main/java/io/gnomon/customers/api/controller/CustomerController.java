package io.gnomon.customers.api.controller;

import io.gnomon.customers.api.response.CustomerPageResponse;
import io.gnomon.customers.api.response.CustomerResponse;
import io.gnomon.customers.application.port.in.AdminCustomerUseCase;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/tenants/{tenantSlug}/customers")
public class CustomerController {
  private final AdminCustomerUseCase customers;

  public CustomerController(AdminCustomerUseCase customers) {
    this.customers = customers;
  }

  @GetMapping
  CustomerPageResponse list(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    var result = customers.list(user.userId(), tenantSlug, page, size);
    return new CustomerPageResponse(
        result.content().stream().map(CustomerResponse::from).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages(),
        result.last());
  }

  @GetMapping("/{id}")
  CustomerResponse get(
      @AuthenticationPrincipal LocalUserPrincipal user,
      @PathVariable String tenantSlug,
      @PathVariable UUID id) {
    return CustomerResponse.from(customers.get(user.userId(), tenantSlug, id));
  }
}
