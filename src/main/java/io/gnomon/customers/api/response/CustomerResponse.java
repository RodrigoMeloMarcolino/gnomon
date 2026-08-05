package io.gnomon.customers.api.response;

import io.gnomon.customers.application.port.in.CustomerResult;
import java.util.UUID;

public record CustomerResponse(UUID id, String name, String phone, String email) {
  public static CustomerResponse from(CustomerResult value) {
    return new CustomerResponse(value.id(), value.name(), value.phone(), value.email());
  }
}
