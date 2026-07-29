package io.gnomon.tenancy.application.port.out;

import io.gnomon.tenancy.domain.model.User;

public interface UserProjectionPort {

  User upsert(String keycloakSubject, String normalizedEmail, String displayName);
}
