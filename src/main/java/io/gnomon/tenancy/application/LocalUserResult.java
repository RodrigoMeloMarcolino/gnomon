package io.gnomon.tenancy.application;

import io.gnomon.tenancy.domain.User;
import java.util.UUID;

public record LocalUserResult(
    UUID userId, String keycloakSubject, String email, String displayName) {

  static LocalUserResult from(User user) {
    return new LocalUserResult(user.id(), user.keycloakSubject(), user.email(), user.displayName());
  }
}
