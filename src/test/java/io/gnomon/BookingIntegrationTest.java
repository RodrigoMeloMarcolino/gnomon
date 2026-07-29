package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class BookingIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
            appointment_slots,
            appointments,
            customers,
            availability_rules,
            calendar_offerings,
            calendars,
            offerings,
            collaborators,
            tenant_memberships,
            tenants,
            users
        CASCADE
        """);
  }

  @Test
  void booking_whenCreatedReplayedOrChanged_shouldReturnStableStatusesAndErrors() throws Exception {
    var scenario = seedScenario(1, 30);
    ZonedDateTime start = scenario.startAt();
    String payload =
        payload(scenario.calendarIds().getFirst(), scenario.offeringId(), start, "+5585999990001");

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("scheduled"))
        .andExpect(jsonPath("$.customer.phone").value("+5585999990001"))
        .andExpect(jsonPath("$.offering.duration_minutes").value(30));

    UUID appointmentId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM appointments WHERE idempotency_key = 'intent-1'", UUID.class);

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(appointmentId.toString()));

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    payload(
                        scenario.calendarIds().getFirst(),
                        scenario.offeringId(),
                        start.plusMinutes(30),
                        "+5585999990001")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("idempotency_key_conflict"));

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "seconds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    payload(
                        scenario.calendarIds().getFirst(),
                        scenario.offeringId(),
                        start.plusSeconds(45),
                        "+5585999990002")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @Test
  void concurrentBooking_whenSameKeyAndPayload_shouldCreateOnceAndReplayOnce() throws Exception {
    var scenario = seedScenario(1, 30);
    String payload =
        payload(
            scenario.calendarIds().getFirst(),
            scenario.offeringId(),
            scenario.startAt(),
            "+5585999990003");

    List<MvcResult> results =
        race(
            request(scenario.tenantSlug(), "same-intent", payload),
            request(scenario.tenantSlug(), "same-intent", payload));

    assertThat(statuses(results)).containsExactlyInAnyOrder(200, 201);
    assertThat(count("appointments")).isOne();
    assertThat(count("appointment_slots")).isEqualTo(2);
    assertThat(count("customers")).isOne();
  }

  @Test
  void concurrentBooking_whenDifferentKeysCompeteForSameSlot_shouldRollbackLoser()
      throws Exception {
    var scenario = seedScenario(1, 30);
    UUID calendarId = scenario.calendarIds().getFirst();

    List<MvcResult> results =
        race(
            request(
                scenario.tenantSlug(),
                "competing-a",
                payload(calendarId, scenario.offeringId(), scenario.startAt(), "+5585999990004")),
            request(
                scenario.tenantSlug(),
                "competing-b",
                payload(calendarId, scenario.offeringId(), scenario.startAt(), "+5585999990005")));

    assertThat(statuses(results)).containsExactlyInAnyOrder(201, 409);
    assertThat(errorCodes(results)).contains("slot_unavailable");
    assertThat(count("appointments")).isOne();
    assertThat(count("appointment_slots")).isEqualTo(2);
    assertThat(count("customers")).isOne();
  }

  @Test
  void concurrentBooking_whenAppointmentsPartiallyOverlap_shouldRejectOneWithConflict()
      throws Exception {
    var scenario = seedScenario(1, 30);
    UUID calendarId = scenario.calendarIds().getFirst();

    List<MvcResult> results =
        race(
            request(
                scenario.tenantSlug(),
                "overlap-a",
                payload(calendarId, scenario.offeringId(), scenario.startAt(), "+5585999990006")),
            request(
                scenario.tenantSlug(),
                "overlap-b",
                payload(
                    calendarId,
                    scenario.offeringId(),
                    scenario.startAt().plusMinutes(15),
                    "+5585999990007")));

    assertThat(statuses(results)).containsExactlyInAnyOrder(201, 409);
    assertThat(count("appointments")).isOne();
    assertThat(count("appointment_slots")).isEqualTo(2);
  }

  @Test
  void concurrentBooking_whenCalendarsDifferAndPhoneMatches_shouldReuseCustomerAndCreateBoth()
      throws Exception {
    var scenario = seedScenario(2, 30);
    String phone = "+5585999990008";

    List<MvcResult> results =
        race(
            request(
                scenario.tenantSlug(),
                "calendar-a",
                payload(
                    scenario.calendarIds().get(0),
                    scenario.offeringId(),
                    scenario.startAt(),
                    phone)),
            request(
                scenario.tenantSlug(),
                "calendar-b",
                payload(
                    scenario.calendarIds().get(1),
                    scenario.offeringId(),
                    scenario.startAt(),
                    phone)));

    assertThat(statuses(results)).containsOnly(201);
    assertThat(count("appointments")).isEqualTo(2);
    assertThat(count("appointment_slots")).isEqualTo(4);
    assertThat(count("customers")).isOne();
  }

  private Callable<MvcResult> request(String tenantSlug, String key, String payload) {
    return () ->
        mockMvc
            .perform(
                post("/v1/public/tenants/{slug}/appointments", tenantSlug)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
            .andReturn();
  }

  private static List<MvcResult> race(Callable<MvcResult> first, Callable<MvcResult> second)
      throws Exception {
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstFuture =
          executor.submit(
              () -> {
                start.await();
                return first.call();
              });
      var secondFuture =
          executor.submit(
              () -> {
                start.await();
                return second.call();
              });
      start.countDown();
      return List.of(firstFuture.get(), secondFuture.get());
    }
  }

  private static List<Integer> statuses(List<MvcResult> results) {
    return results.stream().map(result -> result.getResponse().getStatus()).toList();
  }

  private static List<String> errorCodes(List<MvcResult> results) {
    return results.stream()
        .map(
            result ->
                new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8))
        .filter(body -> body.contains("\"error\""))
        .map(body -> body.replaceAll("(?s).*\"code\":\"([^\"]+)\".*", "$1"))
        .toList();
  }

  private int count(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private Scenario seedScenario(int calendarCount, int durationMinutes) {
    UUID tenantId = UUID.randomUUID();
    UUID offeringId = UUID.randomUUID();
    String tenantSlug = "booking-it-" + tenantId.toString().substring(0, 8);
    LocalDate date = LocalDate.now(ZONE).plusDays(7);
    ZonedDateTime startAt = ZonedDateTime.of(date, LocalTime.of(9, 0), ZONE);

    jdbcTemplate.update(
        """
        INSERT INTO tenants (id, name, slug, timezone, currency_code)
        VALUES (?, 'Booking IT', ?, ?, 'BRL')
        """,
        tenantId,
        tenantSlug,
        ZONE.getId());
    jdbcTemplate.update(
        """
        INSERT INTO offerings (id, tenant_id, title, duration_minutes, price_cents)
        VALUES (?, ?, 'Corte', ?, 4500)
        """,
        offeringId,
        tenantId,
        durationMinutes);

    var calendarIds = new java.util.ArrayList<UUID>();
    for (int index = 0; index < calendarCount; index++) {
      UUID collaboratorId = UUID.randomUUID();
      UUID calendarId = UUID.randomUUID();
      jdbcTemplate.update(
          """
          INSERT INTO collaborators (id, tenant_id, display_name)
          VALUES (?, ?, ?)
          """,
          collaboratorId,
          tenantId,
          "Staff " + index);
      jdbcTemplate.update(
          """
          INSERT INTO calendars (id, tenant_id, collaborator_id, name, timezone)
          VALUES (?, ?, ?, ?, ?)
          """,
          calendarId,
          tenantId,
          collaboratorId,
          "Calendar " + index,
          ZONE.getId());
      jdbcTemplate.update(
          """
          INSERT INTO calendar_offerings (tenant_id, calendar_id, offering_id)
          VALUES (?, ?, ?)
          """,
          tenantId,
          calendarId,
          offeringId);
      jdbcTemplate.update(
          """
          INSERT INTO availability_rules
              (tenant_id, calendar_id, weekday, start_time, end_time)
          VALUES (?, ?, ?, TIME '09:00', TIME '12:00')
          """,
          tenantId,
          calendarId,
          date.getDayOfWeek().getValue());
      calendarIds.add(calendarId);
    }
    return new Scenario(tenantSlug, offeringId, List.copyOf(calendarIds), startAt);
  }

  private static String payload(
      UUID calendarId, UUID offeringId, ZonedDateTime startAt, String phone) {
    return """
        {
          "calendar_id":"%s",
          "offering_id":"%s",
          "start_at":"%s",
          "customer_name":"Customer",
          "customer_phone":"%s",
          "customer_email":"CUSTOMER@EXAMPLE.COM",
          "customer_notes":"Preferência sem PII em logs"
        }
        """
        .formatted(calendarId, offeringId, startAt.toOffsetDateTime(), phone);
  }

  private record Scenario(
      String tenantSlug, UUID offeringId, List<UUID> calendarIds, ZonedDateTime startAt) {}
}
