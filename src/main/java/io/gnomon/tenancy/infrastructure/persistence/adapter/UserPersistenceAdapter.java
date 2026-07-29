package io.gnomon.tenancy.infrastructure.persistence.adapter;

import io.gnomon.tenancy.application.port.out.UserRepository;
import io.gnomon.tenancy.domain.model.User;
import io.gnomon.tenancy.infrastructure.persistence.entity.UserJpaEntity;
import io.gnomon.tenancy.infrastructure.persistence.repository.SpringDataUserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class UserPersistenceAdapter implements UserRepository {

  private final SpringDataUserRepository repository;

  UserPersistenceAdapter(SpringDataUserRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<User> findById(UUID id) {
    return repository.findById(id).map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public Optional<User> findByEmail(String normalizedEmail) {
    return repository.findByEmail(normalizedEmail).map(UserPersistenceAdapter::toDomain);
  }

  @Override
  public List<User> findByIdIn(Collection<UUID> ids) {
    return repository.findByIdIn(ids).stream().map(UserPersistenceAdapter::toDomain).toList();
  }

  static User toDomain(UserJpaEntity entity) {
    return new User(
        entity.id(),
        entity.keycloakSubject(),
        entity.email(),
        entity.displayName(),
        entity.createdAt(),
        entity.updatedAt());
  }
}
