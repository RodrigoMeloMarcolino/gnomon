package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class CatalogSchemaMigrationTest {

  @Container
  static final PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:16-alpine").withStartupTimeout(Duration.ofMinutes(1));

  @BeforeAll
  static void migrateSchema() {
    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @Test
  void migration_shouldCreateCatalogTablesAndExpectedIndexes() throws Exception {
    try (var connection = connection()) {
      assertThat(tableNames(connection))
          .contains("collaborators", "calendars", "offerings", "calendar_offerings");
      assertThat(indexNames(connection, "collaborators"))
          .contains(
              "uq_collaborators_tenant_user",
              "idx_collaborators_tenant_id",
              "idx_collaborators_user_id");
      assertThat(indexNames(connection, "calendars"))
          .contains("idx_calendars_tenant_id", "idx_calendars_collaborator_id");
      assertThat(indexNames(connection, "offerings"))
          .contains("uq_offerings_active_tenant_title", "idx_offerings_tenant_active");
      assertThat(indexNames(connection, "calendar_offerings"))
          .contains(
              "idx_calendar_offerings_tenant_calendar",
              "idx_calendar_offerings_tenant_offering",
              "idx_calendar_offerings_offering_id");
      assertThat(columnNames(connection, "calendar_offerings"))
          .containsExactlyInAnyOrder("tenant_id", "calendar_id", "offering_id", "created_at");
    }
  }

  @Test
  void collaborators_shouldEnforcePartialUserUniquenessPerTenant() throws Exception {
    var tenantA = insertTenant("collaborator-a");
    var tenantB = insertTenant("collaborator-b");
    var user = insertUser("collaborator-user");

    insertCollaborator(tenantA, null, "Unlinked One");
    insertCollaborator(tenantA, null, "Unlinked Two");
    insertCollaborator(tenantA, user, "Linked A");
    insertCollaborator(tenantB, user, "Linked B");

    assertThatThrownBy(() -> insertCollaborator(tenantA, user, "Duplicate"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_collaborators_tenant_user");
  }

  @Test
  void calendars_shouldEnforceOneToOneAndRejectCrossTenantCollaborator() throws Exception {
    var tenantA = insertTenant("calendar-a");
    var tenantB = insertTenant("calendar-b");
    var collaborator = insertCollaborator(tenantA, null, "Calendar Owner");

    insertCalendar(tenantA, collaborator, "Primary");

    assertThatThrownBy(() -> insertCalendar(tenantA, collaborator, "Duplicate"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_calendars_tenant_collaborator");
    assertThatThrownBy(() -> insertCalendar(tenantB, collaborator, "Cross Tenant"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("fk_calendars_tenant_collaborator");
  }

  @Test
  void offerings_shouldEnforceActiveTitleDurationAndPriceRules() throws Exception {
    var tenant = insertTenant("offering-rules");

    insertOffering(tenant, "Haircut", 30, 5000, true);
    insertOffering(tenant, "HAIRCUT", 30, 5000, false);

    assertThatThrownBy(() -> insertOffering(tenant, "haircut", 30, 5000, true))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_offerings_active_tenant_title");
    assertThatThrownBy(() -> insertOffering(tenant, "Invalid Duration", 20, 5000, true))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_offerings_duration");
    assertThatThrownBy(() -> insertOffering(tenant, "Invalid Price", 30, -1, true))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_offerings_price");
  }

  @Test
  void calendarOfferings_shouldRejectCrossTenantReferences() throws Exception {
    var tenantA = insertTenant("assignment-a");
    var tenantB = insertTenant("assignment-b");
    var collaboratorA = insertCollaborator(tenantA, null, "A");
    var calendarA = insertCalendar(tenantA, collaboratorA, "A");
    var offeringA = insertOffering(tenantA, "A", 15, null, true);
    var offeringB = insertOffering(tenantB, "B", 15, null, true);

    insertCalendarOffering(tenantA, calendarA, offeringA);

    assertThatThrownBy(() -> insertCalendarOffering(tenantA, calendarA, offeringB))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("fk_calendar_offerings_tenant_offering");
    assertThatThrownBy(() -> insertCalendarOffering(tenantB, calendarA, offeringB))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("fk_calendar_offerings_tenant_calendar");
  }

  @Test
  void mutableCatalogTables_shouldUseUpdatedAtTrigger() throws Exception {
    var tenant = insertTenant("updated-at");
    var collaborator = insertCollaborator(tenant, null, "Before");

    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                UPDATE collaborators
                SET display_name = ?, updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z'
                WHERE id = ?
                RETURNING updated_at
                """)) {
      statement.setString(1, "After");
      statement.setObject(2, collaborator);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getTimestamp("updated_at").toInstant())
            .isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
      }
    }
  }

  private static Connection connection() throws SQLException {
    return java.sql.DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static Set<String> tableNames(Connection connection) throws SQLException {
    try (var statement =
            connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'");
        var result = statement.executeQuery()) {
      return values(result, "table_name");
    }
  }

  private static Set<String> columnNames(Connection connection, String table) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = ?
            """)) {
      statement.setString(1, table);
      try (var result = statement.executeQuery()) {
        return values(result, "column_name");
      }
    }
  }

  private static Set<String> indexNames(Connection connection, String table) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?")) {
      statement.setString(1, table);
      try (var result = statement.executeQuery()) {
        return values(result, "indexname");
      }
    }
  }

  private static Set<String> values(ResultSet result, String column) throws SQLException {
    var builder = java.util.stream.Stream.<String>builder();
    while (result.next()) {
      builder.add(result.getString(column));
    }
    return builder.build().collect(Collectors.toSet());
  }

  private static UUID insertTenant(String suffix) throws SQLException {
    var id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO tenants (id, name, slug, timezone)
                VALUES (?, ?, ?, 'America/Fortaleza')
                """)) {
      statement.setObject(1, id);
      statement.setString(2, "Tenant " + suffix);
      statement.setString(3, suffix + "-" + id);
      statement.executeUpdate();
      return id;
    }
  }

  private static UUID insertUser(String suffix) throws SQLException {
    var id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO users (id, keycloak_sub, email, display_name)
                VALUES (?, ?, ?, ?)
                """)) {
      statement.setObject(1, id);
      statement.setString(2, suffix + "-" + id);
      statement.setString(3, suffix + "-" + id + "@gnomon.local");
      statement.setString(4, "User " + suffix);
      statement.executeUpdate();
      return id;
    }
  }

  private static UUID insertCollaborator(UUID tenantId, UUID userId, String name)
      throws SQLException {
    var id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO collaborators (id, tenant_id, user_id, display_name)
                VALUES (?, ?, ?, ?)
                """)) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setObject(3, userId);
      statement.setString(4, name);
      statement.executeUpdate();
      return id;
    }
  }

  private static UUID insertCalendar(UUID tenantId, UUID collaboratorId, String name)
      throws SQLException {
    var id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO calendars (id, tenant_id, collaborator_id, name, timezone)
                VALUES (?, ?, ?, ?, 'America/Fortaleza')
                """)) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setObject(3, collaboratorId);
      statement.setString(4, name);
      statement.executeUpdate();
      return id;
    }
  }

  private static UUID insertOffering(
      UUID tenantId, String title, int durationMinutes, Integer priceCents, boolean active)
      throws SQLException {
    var id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO offerings
                    (id, tenant_id, title, duration_minutes, price_cents, is_active)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setString(3, title);
      statement.setInt(4, durationMinutes);
      statement.setObject(5, priceCents);
      statement.setBoolean(6, active);
      statement.executeUpdate();
      return id;
    }
  }

  private static void insertCalendarOffering(UUID tenantId, UUID calendarId, UUID offeringId)
      throws SQLException {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO calendar_offerings (tenant_id, calendar_id, offering_id)
                VALUES (?, ?, ?)
                """)) {
      statement.setObject(1, tenantId);
      statement.setObject(2, calendarId);
      statement.setObject(3, offeringId);
      statement.executeUpdate();
    }
  }
}
