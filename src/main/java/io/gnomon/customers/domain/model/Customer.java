package io.gnomon.customers.domain.model;

import io.gnomon.customers.domain.exception.CustomerException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Customer(UUID id, String name, String phone, String email) {

  private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{1,14}");

  public Customer {
    Objects.requireNonNull(id, "id is required");
    name = requireName(name);
    phone = requireCanonicalPhone(phone);
    email = normalizeEmail(email);
  }

  private static String requireName(String value) {
    if (value == null || value.isBlank()) {
      throw new CustomerException("validation_error", "customer_name is required");
    }
    String normalized = value.strip();
    if (normalized.length() > 120) {
      throw new CustomerException(
          "validation_error", "customer_name must contain at most 120 characters");
    }
    return normalized;
  }

  private static String requireCanonicalPhone(String value) {
    if (value == null || !E164.matcher(value).matches()) {
      throw new CustomerException("phone_invalid", "customer_phone must be canonical E.164");
    }
    return value;
  }

  private static String normalizeEmail(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.strip().toLowerCase(Locale.ROOT);
  }
}
