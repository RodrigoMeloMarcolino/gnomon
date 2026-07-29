package io.gnomon.customers.application.port.out;

import io.gnomon.customers.domain.model.Customer;

public interface CustomerRepository {

  Customer findOrCreate(String name, String canonicalPhone, String normalizedEmail);
}
