package io.gnomon.tenancy.application.port;

import io.gnomon.tenancy.domain.User;

public interface UserProjectionPort {

  User upsert(String keycloakSubject, String normalizedEmail, String displayName);
}
