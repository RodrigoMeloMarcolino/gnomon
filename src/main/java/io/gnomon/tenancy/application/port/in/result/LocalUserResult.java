package io.gnomon.tenancy.application.port.in.result;

import io.gnomon.tenancy.domain.model.User;
import java.util.UUID;

public record LocalUserResult(
    UUID userId, String keycloakSubject, String email, String displayName) {

  public static LocalUserResult from(User user) {
    return new LocalUserResult(user.id(), user.keycloakSubject(), user.email(), user.displayName());
  }
}
