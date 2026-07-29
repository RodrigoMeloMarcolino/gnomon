package io.gnomon.booking.api.response;

import io.gnomon.booking.application.port.in.CustomerSummary;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record CustomerResponse(UUID id, String name, String phone, String email) {
  static CustomerResponse from(CustomerSummary customer) {
    return new CustomerResponse(customer.id(), customer.name(), customer.phone(), customer.email());
  }
}
