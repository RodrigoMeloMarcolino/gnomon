package io.gnomon.tenancy.application.port.out;

import io.gnomon.tenancy.domain.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

  Optional<User> findById(UUID id);

  Optional<User> findByEmail(String normalizedEmail);

  List<User> findByIdIn(Collection<UUID> ids);
}
