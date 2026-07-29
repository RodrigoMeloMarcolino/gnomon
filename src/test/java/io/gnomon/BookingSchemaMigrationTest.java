package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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
class BookingSchemaMigrationTest {

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
  void migration_shouldCreateBookingTablesAndRequiredIndexes() throws Exception {
    try (var connection = connection();
        var tableStatement =
            connection.prepareStatement(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('customers', 'appointments', 'appointment_slots')
                """);
        var tableResult = tableStatement.executeQuery()) {
      var tables = new HashSet<String>();
      while (tableResult.next()) {
        tables.add(tableResult.getString("table_name"));
      }
      assertThat(tables)
          .containsExactlyInAnyOrder("customers", "appointments", "appointment_slots");
    }

    assertIndexes(
        "appointments",
        "idx_appointments_tenant_calendar_start",
        "idx_appointments_calendar_start",
        "idx_appointments_tenant_offering",
        "idx_appointments_offering_id",
        "idx_appointments_customer_id",
        "idx_appointments_tenant_status_start");
    assertIndexes(
        "appointment_slots",
        "idx_appointment_slots_tenant_appointment",
        "idx_appointment_slots_appointment_id",
        "idx_appointment_slots_tenant_calendar");
  }

  @Test
  void schema_shouldKeepFutureTokensOutAndSlotsAppendOnly() throws Exception {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name IN ('appointments', 'appointment_slots')
                """);
        var result = statement.executeQuery()) {
      var columns = new HashSet<String>();
      while (result.next()) {
        columns.add(result.getString("column_name"));
      }
      assertThat(columns)
          .doesNotContain("cancel_token_hash", "reschedule_token_hash")
          .contains("created_at");
    }

    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'appointment_slots'
                  AND column_name = 'updated_at'
                """);
        var result = statement.executeQuery()) {
      assertThat(result.next()).isTrue();
      assertThat(result.getInt(1)).isZero();
    }
  }

  @Test
  void bookingConstraints_shouldRejectDuplicatePhoneIdempotencyAndOccupiedSlot() throws Exception {
    var catalog = insertCatalog("booking-unique");
    UUID customer = insertCustomer("+5585999990001");
    UUID appointment =
        insertAppointment(catalog, customer, "booking-key", "a".repeat(64), "scheduled");
    UUID competingAppointment =
        insertAppointment(catalog, customer, "competing-key", "f".repeat(64), "scheduled");
    insertSlot(
        catalog.tenantId(),
        appointment,
        catalog.calendarId(),
        Instant.parse("2027-07-01T12:00:00Z"));

    assertThatThrownBy(() -> insertCustomer("+5585999990001"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_customers_phone");
    assertThatThrownBy(
            () -> insertAppointment(catalog, customer, "booking-key", "b".repeat(64), "scheduled"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_appointments_tenant_idempotency_key");
    assertThatThrownBy(
            () ->
                insertSlot(
                    catalog.tenantId(),
                    competingAppointment,
                    catalog.calendarId(),
                    Instant.parse("2027-07-01T12:00:00Z")))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("uq_appointment_slots_calendar_start");
  }

  @Test
  void appointmentConstraints_shouldRejectInvalidStatusDurationAndCrossTenantCalendar()
      throws Exception {
    var catalogA = insertCatalog("booking-check-a");
    var catalogB = insertCatalog("booking-check-b");
    UUID customer = insertCustomer("+5585999990002");

    assertThatThrownBy(
            () ->
                insertAppointment(catalogA, customer, "invalid-status", "c".repeat(64), "pending"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("ck_appointments_status");
    assertThatThrownBy(
            () ->
                insertAppointment(
                    new CatalogIds(
                        catalogA.tenantId(), catalogB.calendarId(), catalogA.offeringId()),
                    customer,
                    "cross-tenant",
                    "d".repeat(64),
                    "scheduled"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("fk_appointments_tenant_calendar");
  }

  @Test
  void customersAndAppointments_shouldMaintainUpdatedAtWithTriggers() throws Exception {
    var catalog = insertCatalog("booking-updated-at");
    UUID customer = insertCustomer("+5585999990003");
    UUID appointment =
        insertAppointment(catalog, customer, "updated-at", "e".repeat(64), "scheduled");

    assertTriggerMaintainsUpdatedAt(customer, "customers", "name = 'Updated customer'");
    assertTriggerMaintainsUpdatedAt(
        appointment, "appointments", "customer_notes = 'Updated notes'");
  }

  private static void assertIndexes(String table, String... expected) throws SQLException {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = ?
                """)) {
      statement.setString(1, table);
      try (var result = statement.executeQuery()) {
        var indexes = new HashSet<String>();
        while (result.next()) {
          indexes.add(result.getString("indexname"));
        }
        assertThat(indexes).contains(expected);
      }
    }
  }

  private static CatalogIds insertCatalog(String slug) throws SQLException {
    UUID tenant = UUID.randomUUID();
    UUID collaborator = UUID.randomUUID();
    UUID calendar = UUID.randomUUID();
    UUID offering = UUID.randomUUID();
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
                  INSERT INTO calendars (id, tenant_id, collaborator_id, name, timezone)
                  VALUES (?, ?, ?, 'Agenda', 'America/Fortaleza')
                  """);
          var offeringStatement =
              connection.prepareStatement(
                  """
                  INSERT INTO offerings (id, tenant_id, title, duration_minutes)
                  VALUES (?, ?, ?, 30)
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
        offeringStatement.setObject(1, offering);
        offeringStatement.setObject(2, tenant);
        offeringStatement.setString(3, "Offering " + offering);
        offeringStatement.executeUpdate();
        connection.commit();
      }
    }
    return new CatalogIds(tenant, calendar, offering);
  }

  private static UUID insertCustomer(String phone) throws SQLException {
    UUID id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO customers (id, name, phone)
                VALUES (?, 'Customer', ?)
                """)) {
      statement.setObject(1, id);
      statement.setString(2, phone);
      statement.executeUpdate();
      return id;
    }
  }

  private static UUID insertAppointment(
      CatalogIds catalog, UUID customerId, String idempotencyKey, String fingerprint, String status)
      throws SQLException {
    UUID id = UUID.randomUUID();
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO appointments (
                    id, tenant_id, calendar_id, offering_id, customer_id,
                    start_at, end_at, duration_minutes_snapshot,
                    calendar_timezone_snapshot, status,
                    idempotency_key, idempotency_fingerprint
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    TIMESTAMPTZ '2027-07-01 12:00:00Z',
                    TIMESTAMPTZ '2027-07-01 12:30:00Z',
                    30, 'America/Fortaleza', ?, ?, ?
                )
                """)) {
      statement.setObject(1, id);
      statement.setObject(2, catalog.tenantId());
      statement.setObject(3, catalog.calendarId());
      statement.setObject(4, catalog.offeringId());
      statement.setObject(5, customerId);
      statement.setString(6, status);
      statement.setString(7, idempotencyKey);
      statement.setString(8, fingerprint);
      statement.executeUpdate();
      return id;
    }
  }

  private static void insertSlot(
      UUID tenantId, UUID appointmentId, UUID calendarId, Instant slotStartAt) throws SQLException {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO appointment_slots
                    (tenant_id, appointment_id, calendar_id, slot_start_at)
                VALUES (?, ?, ?, ?)
                """)) {
      statement.setObject(1, tenantId);
      statement.setObject(2, appointmentId);
      statement.setObject(3, calendarId);
      statement.setTimestamp(4, java.sql.Timestamp.from(slotStartAt));
      statement.executeUpdate();
    }
  }

  private static void assertTriggerMaintainsUpdatedAt(UUID id, String table, String mutation)
      throws SQLException {
    try (var connection = connection();
        var statement =
            connection.prepareStatement(
                """
                UPDATE %s
                SET %s,
                    updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z'
                WHERE id = ?
                RETURNING updated_at
                """
                    .formatted(table, mutation))) {
      statement.setObject(1, id);
      try (var result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        assertThat(result.getTimestamp("updated_at").toInstant())
            .isAfter(Instant.parse("2020-01-01T00:00:00Z"));
      }
    }
  }

  private static Connection connection() throws SQLException {
    return java.sql.DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private record CatalogIds(UUID tenantId, UUID calendarId, UUID offeringId) {}
}
