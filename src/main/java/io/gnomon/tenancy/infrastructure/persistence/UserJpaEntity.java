package io.gnomon.tenancy.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
class UserJpaEntity {

  @Id
  @Column(nullable = false, updatable = false)
  private UUID id;

  @Column(name = "keycloak_sub", nullable = false, unique = true)
  private String keycloakSubject;

  @Column(nullable = false, unique = true, columnDefinition = "citext")
  @JdbcTypeCode(SqlTypes.OTHER)
  private String email;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserJpaEntity() {}

  UUID id() {
    return id;
  }

  String keycloakSubject() {
    return keycloakSubject;
  }

  String email() {
    return email;
  }

  String displayName() {
    return displayName;
  }

  Instant createdAt() {
    return createdAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }
}
