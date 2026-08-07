package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AdminPanelIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");
  private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID STAFF_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID OFFERING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID STAFF_CALENDAR_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000002");
  private static final UUID OTHER_CALENDAR_ID =
      UUID.fromString("40000000-0000-0000-0000-000000000003");
  private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID APPOINTMENT_ID =
      UUID.fromString("60000000-0000-0000-0000-000000000001");
  private static final UUID SECOND_APPOINTMENT_ID =
      UUID.fromString("60000000-0000-0000-0000-000000000002");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbc;

  private MockMvc mockMvc;
  private String date;
  private String from;
  private String to;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    jdbc.execute(
        """
        TRUNCATE TABLE appointment_slots, appointments, customers, availability_rules,
            calendar_offerings, calendars, offerings, collaborators, tenant_memberships,
            tenants, users CASCADE
        """);
    seed();
  }

  @Test
  void list_whenOwnerUsesFiltersAndPagination_shouldReturnTenantScopedPage() throws Exception {
    mockMvc
        .perform(
            get("/v1/tenants/salon-a/appointments")
                .with(identity("owner-a", "owner-a@example.test", "Owner A"))
                .param("from", from)
                .param("to", to)
                .param("calendar_id", CALENDAR_ID.toString())
                .param("status", "scheduled")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()", org.hamcrest.Matchers.is(1)))
        .andExpect(jsonPath("$.content[0].id").value(APPOINTMENT_ID.toString()))
        .andExpect(jsonPath("$.total_elements").value(1))
        .andExpect(jsonPath("$.size").value(1));

    mockMvc
        .perform(
            get("/v1/tenants/salon-a/customers")
                .with(identity("owner-a", "owner-a@example.test", "Owner A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()", org.hamcrest.Matchers.is(1)))
        .andExpect(jsonPath("$.content[0].id").value(CUSTOMER_ID.toString()));
  }

  @Test
  void list_whenStaffRequestsAnotherCalendar_shouldReturnForbidden() throws Exception {
    mockMvc
        .perform(
            get("/v1/tenants/salon-a/appointments")
                .with(identity("staff-a", "staff-a@example.test", "Staff A"))
                .param("from", from)
                .param("to", to)
                .param("calendar_id", CALENDAR_ID.toString()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("staff_calendar_mismatch"));

    mockMvc
        .perform(
            get("/v1/tenants/salon-a/appointments")
                .with(identity("staff-a", "staff-a@example.test", "Staff A"))
                .param("from", from)
                .param("to", to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()", org.hamcrest.Matchers.is(1)))
        .andExpect(jsonPath("$.content[0].calendar.id").value(STAFF_CALENDAR_ID.toString()));
  }

  @Test
  void crossTenant_whenReadingAppointmentOrCustomer_shouldReturnForbidden() throws Exception {
    mockMvc
        .perform(
            get("/v1/tenants/salon-b/appointments/{id}", APPOINTMENT_ID)
                .with(identity("owner-b", "owner-b@example.test", "Owner B")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("appointment_access_denied"));

    mockMvc
        .perform(
            get("/v1/tenants/salon-b/customers/{id}", CUSTOMER_ID)
                .with(identity("owner-b", "owner-b@example.test", "Owner B")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("customer_access_denied"));
  }

  @Test
  void cancel_whenAppointmentIsScheduled_shouldReleaseSlotsAndRejectSecondTransition()
      throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/salon-a/appointments/{id}/cancel", APPOINTMENT_ID)
                .with(identity("owner-a", "owner-a@example.test", "Owner A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelled"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment_slots", Integer.class))
        .isZero();

    mockMvc
        .perform(
            post("/v1/tenants/salon-a/appointments/{id}/complete", APPOINTMENT_ID)
                .with(identity("owner-a", "owner-a@example.test", "Owner A")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("appointment_status_conflict"));
  }

  @Test
  void transition_whenTwoRequestsRace_shouldAllowOnlyOneStateChange() throws Exception {
    var start = new CountDownLatch(1);
    Callable<MvcResult> cancel = transitionRequest(start, "/cancel");
    Callable<MvcResult> complete = transitionRequest(start, "/complete");
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures = executor.invokeAll(List.of(cancel, complete));
      var statuses = futures.stream().map(this::statusOf).sorted().toList();
      assertThat(statuses).containsExactly(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM appointments WHERE status <> 'scheduled'", Integer.class))
        .isOne();
  }

  private Callable<MvcResult> transitionRequest(CountDownLatch start, String action) {
    return () -> {
      start.await();
      return mockMvc
          .perform(
              post("/v1/tenants/salon-a/appointments/{id}" + action, APPOINTMENT_ID)
                  .with(identity("owner-a", "owner-a@example.test", "Owner A")))
          .andReturn();
    };
  }

  private int statusOf(java.util.concurrent.Future<MvcResult> future) {
    try {
      return future.get().getResponse().getStatus();
    } catch (Exception exception) {
      throw new AssertionError("concurrent admin request failed", exception);
    }
  }

  private void seed() {
    LocalDate localDate = LocalDate.now(ZONE).plusDays(7);
    Instant startAt = localDate.atTime(9, 0).atZone(ZONE).toInstant();
    date = localDate.toString();
    from = startAt.minusSeconds(3600).toString();
    to = startAt.plusSeconds(86400).toString();

    jdbc.update(
        "INSERT INTO users (id,keycloak_sub,email,display_name) VALUES (?,?,?,?)",
        OWNER_ID,
        "owner-a",
        "owner-a@example.test",
        "Owner A");
    jdbc.update(
        "INSERT INTO users (id,keycloak_sub,email,display_name) VALUES (?,?,?,?)",
        STAFF_ID,
        "staff-a",
        "staff-a@example.test",
        "Staff A");
    jdbc.update(
        "INSERT INTO tenants (id,name,slug,timezone,currency_code) VALUES (?,?,?,?,?)",
        TENANT_ID,
        "Salon A",
        "salon-a",
        ZONE.getId(),
        "BRL");
    jdbc.update(
        "INSERT INTO tenants (id,name,slug,timezone,currency_code) VALUES (?,?,?,?,?)",
        OTHER_TENANT_ID,
        "Salon B",
        "salon-b",
        ZONE.getId(),
        "BRL");
    jdbc.update(
        "INSERT INTO tenant_memberships (tenant_id,user_id,role) VALUES (?,?,?)",
        TENANT_ID,
        OWNER_ID,
        "owner");
    jdbc.update(
        "INSERT INTO tenant_memberships (tenant_id,user_id,role) VALUES (?,?,?)",
        TENANT_ID,
        STAFF_ID,
        "staff");
    jdbc.update(
        "INSERT INTO tenant_memberships (tenant_id,user_id,role) VALUES (?,?,?)",
        OTHER_TENANT_ID,
        OWNER_ID,
        "owner");
    jdbc.update(
        "INSERT INTO offerings (id,tenant_id,title,duration_minutes,price_cents) VALUES (?,?,?,?,?)",
        OFFERING_ID,
        TENANT_ID,
        "Corte",
        30,
        4500);
    insertCalendar(CALENDAR_ID, UUID.randomUUID(), "Agenda A");
    insertCalendar(STAFF_CALENDAR_ID, UUID.randomUUID(), "Agenda Staff");
    insertCalendar(OTHER_CALENDAR_ID, UUID.randomUUID(), "Agenda B");
    jdbc.update(
        "UPDATE collaborators SET user_id=? WHERE id=(SELECT collaborator_id FROM calendars WHERE id=?)",
        STAFF_ID,
        STAFF_CALENDAR_ID);
    jdbc.update(
        "INSERT INTO calendar_offerings (tenant_id,calendar_id,offering_id) VALUES (?,?,?)",
        TENANT_ID,
        CALENDAR_ID,
        OFFERING_ID);
    jdbc.update(
        "INSERT INTO calendar_offerings (tenant_id,calendar_id,offering_id) VALUES (?,?,?)",
        TENANT_ID,
        STAFF_CALENDAR_ID,
        OFFERING_ID);
    jdbc.update(
        "INSERT INTO availability_rules (tenant_id,calendar_id,weekday,start_time,end_time) VALUES (?,?,?,TIME '08:00',TIME '18:00')",
        TENANT_ID,
        CALENDAR_ID,
        localDate.getDayOfWeek().getValue());
    jdbc.update(
        "INSERT INTO customers (id,name,phone,email) VALUES (?,?,?,?)",
        CUSTOMER_ID,
        "Customer",
        "+5585999999999",
        "customer@example.test");
    insertAppointment(APPOINTMENT_ID, CALENDAR_ID, startAt, "scheduled", "key-1");
    insertAppointment(
        SECOND_APPOINTMENT_ID, STAFF_CALENDAR_ID, startAt.plusSeconds(3600), "scheduled", "key-2");
    jdbc.update(
        "INSERT INTO appointment_slots (tenant_id,appointment_id,calendar_id,slot_start_at) VALUES (?,?,?,?)",
        TENANT_ID,
        APPOINTMENT_ID,
        CALENDAR_ID,
        startAt);
    jdbc.update(
        "INSERT INTO appointment_slots (tenant_id,appointment_id,calendar_id,slot_start_at) VALUES (?,?,?,?)",
        TENANT_ID,
        APPOINTMENT_ID,
        CALENDAR_ID,
        startAt.plusSeconds(900));
  }

  private void insertCalendar(UUID id, UUID collaboratorId, String name) {
    jdbc.update(
        "INSERT INTO collaborators (id,tenant_id,display_name) VALUES (?,?,?)",
        collaboratorId,
        TENANT_ID,
        name);
    jdbc.update(
        "INSERT INTO calendars (id,tenant_id,collaborator_id,name,timezone) VALUES (?,?,?,?,?)",
        id,
        TENANT_ID,
        collaboratorId,
        name,
        ZONE.getId());
  }

  private void insertAppointment(
      UUID id, UUID calendarId, Instant startAt, String status, String key) {
    jdbc.update(
        """
        INSERT INTO appointments (id,tenant_id,calendar_id,offering_id,customer_id,start_at,end_at,
            duration_minutes_snapshot,calendar_timezone_snapshot,status,customer_notes,idempotency_key,idempotency_fingerprint)
        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
        """,
        id,
        TENANT_ID,
        calendarId,
        OFFERING_ID,
        CUSTOMER_ID,
        startAt,
        startAt.plusSeconds(1800),
        30,
        ZONE.getId(),
        status,
        "note",
        key,
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  }

  private static JwtRequestPostProcessor identity(String subject, String email, String name) {
    return jwt()
        .jwt(
            token ->
                token
                    .subject(subject)
                    .claim("email", email)
                    .claim("name", name)
                    .claim("email_verified", true));
  }
}
