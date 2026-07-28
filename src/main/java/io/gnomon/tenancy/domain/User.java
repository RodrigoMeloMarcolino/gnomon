package io.gnomon.tenancy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record User(
    UUID id,
    String keycloakSubject,
    String email,
    String displayName,
    Instant createdAt,
    Instant updatedAt) {

  public User {
    Objects.requireNonNull(id, "id");
    keycloakSubject = requireText(keycloakSubject, "keycloakSubject");
    email = normalizeEmail(email);
    displayName = requireText(displayName, "displayName");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static String normalizeEmail(String email) {
    String normalized = requireText(email, "email").toLowerCase(Locale.ROOT);
    if (!normalized.contains("@")) {
      throw new TenancyException("validation_error", "email must be valid");
    }
    return normalized;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new TenancyException("validation_error", field + " must not be blank");
    }
    return value.strip();
  }
}
