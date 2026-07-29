package io.gnomon.tenancy.infrastructure.persistence.repository;

import io.gnomon.tenancy.infrastructure.persistence.entity.UserJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity, UUID> {

  @Query(value = "SELECT * FROM users WHERE email = CAST(:email AS citext)", nativeQuery = true)
  Optional<UserJpaEntity> findByEmail(@Param("email") String email);

  List<UserJpaEntity> findByIdIn(Collection<UUID> ids);
}
