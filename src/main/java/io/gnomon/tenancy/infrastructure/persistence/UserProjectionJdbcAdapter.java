package io.gnomon.tenancy.infrastructure.persistence;

import io.gnomon.tenancy.application.port.UserProjectionPort;
import io.gnomon.tenancy.domain.TenancyException;
import io.gnomon.tenancy.domain.User;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class UserProjectionJdbcAdapter implements UserProjectionPort {

  private static final String UPSERT =
      """
      INSERT INTO users (keycloak_sub, email, display_name)
      VALUES (?, ?, ?)
      ON CONFLICT (keycloak_sub) DO UPDATE
      SET email = EXCLUDED.email,
          display_name = EXCLUDED.display_name
      RETURNING id, keycloak_sub, email, display_name, created_at, updated_at
      """;

  private final JdbcTemplate jdbcTemplate;

  UserProjectionJdbcAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public User upsert(String keycloakSubject, String normalizedEmail, String displayName) {
    try {
      return jdbcTemplate.queryForObject(
          UPSERT, UserProjectionJdbcAdapter::map, keycloakSubject, normalizedEmail, displayName);
    } catch (DataIntegrityViolationException exception) {
      if (ConstraintNames.contains(exception, "uq_users_email")) {
        throw new TenancyException(
            "identity_email_conflict", "email belongs to another Keycloak identity");
      }
      throw exception;
    }
  }

  private static User map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new User(
        resultSet.getObject("id", java.util.UUID.class),
        resultSet.getString("keycloak_sub"),
        resultSet.getString("email"),
        resultSet.getString("display_name"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }
}
