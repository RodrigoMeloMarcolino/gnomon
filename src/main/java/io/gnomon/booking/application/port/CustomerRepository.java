package io.gnomon.booking.application.port;

import io.gnomon.booking.domain.Customer;

public interface CustomerRepository {

  Customer findOrCreate(String name, String canonicalPhone, String normalizedEmail);
}
