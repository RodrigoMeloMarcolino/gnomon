package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@Testcontainers
class AvailabilitySchemaMigrationTest {

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
  void migration_shouldCreateAvailabilityRulesWithExpectedIndexes() throws Exception {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'availability_rules'
                """);
        var result = statement.executeQuery()) {
      var indexes = new java.util.HashSet<String>();
      while (result.next()) {
        indexes.add(result.getString("indexname"));
      }
      assertThat(indexes)
          .contains(
              "idx_availability_rules_tenant_calendar_weekday",
              "idx_availability_rules_calendar_weekday");
    }
  }

  @Test
  void availabilityRules_shouldRejectMisalignedInvertedAndInvalidWeekday() throws Exception {
    var calendar = insertCalendar("availability-checks");

    assertThatThrownBy(
            () -> insertRule(calendar.tenantId(), calendar.calendarId(), 1, "09:07", "12:00"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_availability_rules_time_alignment");
    assertThatThrownBy(
            () -> insertRule(calendar.tenantId(), calendar.calendarId(), 1, "12:00", "09:00"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_availability_rules_time_order");
    assertThatThrownBy(
            () -> insertRule(calendar.tenantId(), calendar.calendarId(), 8, "09:00", "12:00"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_availability_rules_weekday");
  }

  @Test
  void availabilityRules_shouldRejectCrossTenantCalendar() throws Exception {
    var calendarA = insertCalendar("availability-a");
    var calendarB = insertCalendar("availability-b");

    assertThatThrownBy(
            () -> insertRule(calendarA.tenantId(), calendarB.calendarId(), 1, "09:00", "12:00"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("fk_availability_rules_tenant_calendar");
  }

  @Test
  void availabilityRules_shouldMaintainUpdatedAtWithTrigger() throws Exception {
    var calendar = insertCalendar("availability-updated-at");
    UUID rule = insertRule(calendar.tenantId(), calendar.calendarId(), 1, "09:00", "12:00");

    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                UPDATE availability_rules
                SET is_active = FALSE,
                    updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z'
                WHERE id = ?
                RETURNING is_active, updated_at
                """)) {
      statement.setObject(1, rule);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getBoolean("is_active")).isFalse();
        assertThat(result.getTimestamp("updated_at").toInstant())
            .isAfter(java.time.Instant.parse("2020-01-01T00:00:00Z"));
      }
    }
  }

  private static CalendarIds insertCalendar(String slug) throws SQLException {
    UUID tenant = UUID.randomUUID();
    UUID collaborator = UUID.randomUUID();
    UUID calendar = UUID.randomUUID();
    try (var connection = connection()) {
      connection.setAutoCommit(false);
      try (var tenantStatement =
              connection.prepareStatement(
                  """
                  INSERT INTO tenants (id, name, slug, timezone)
                  VALUES (?, ?, ?, 'America/Fortaleza')
                  """);
          var collaboratorStatement =
              connection.prepareStatement(
                  """
                  INSERT INTO collaborators (id, tenant_id, display_name)
                  VALUES (?, ?, 'Staff')
                  """);
          var calendarStatement =
              connection.prepareStatement(
                  """
                  INSERT INTO calendars
                      (id, tenant_id, collaborator_id, name, timezone)
                  VALUES (?, ?, ?, 'Agenda', 'America/Fortaleza')
                  """)) {
        tenantStatement.setObject(1, tenant);
        tenantStatement.setString(2, "Tenant " + slug);
        tenantStatement.setString(3, slug + "-" + tenant);
        tenantStatement.executeUpdate();
        collaboratorStatement.setObject(1, collaborator);
        collaboratorStatement.setObject(2, tenant);
        collaboratorStatement.executeUpdate();
        calendarStatement.setObject(1, calendar);
        calendarStatement.setObject(2, tenant);
        calendarStatement.setObject(3, collaborator);
        calendarStatement.executeUpdate();
        connection.commit();
      }
    }
    return new CalendarIds(tenant, calendar);
  }

  private static UUID insertRule(
      UUID tenantId, UUID calendarId, int weekday, String startTime, String endTime)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO availability_rules
                    (id, tenant_id, calendar_id, weekday, start_time, end_time)
                VALUES (?, ?, ?, ?, ?::time, ?::time)
                """)) {
      statement.setObject(1, id);
      statement.setObject(2, tenantId);
      statement.setObject(3, calendarId);
      statement.setInt(4, weekday);
      statement.setString(5, startTime);
      statement.setString(6, endTime);
      statement.executeUpdate();
      return id;
    }
  }

  private static Connection connection() throws SQLException {
    return java.sql.DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private record CalendarIds(UUID tenantId, UUID calendarId) {}
}
