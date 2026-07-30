package io.gnomon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Verifies the phase-05 Redis behavior against real PostgreSQL and Redis containers. */
@Tag("integration")
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(OrderAnnotation.class)
class RedisCacheIntegrationTest {

  private static final ZoneId ZONE = ZoneId.of("America/Fortaleza");

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.data.redis.host", redis::getHost);
    properties.add("spring.data.redis.port", redis::getFirstMappedPort);
  }

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
  @Order(1)
  void publicAvailability_whenBookingNearLocalMidnight_shouldInvalidateTheCalendarLocalDay()
      throws Exception {
    Scenario scenario = seedScenario();

    mockMvc
        .perform(availableSlots(scenario))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.available_start_times").isArray())
        .andExpect(
            jsonPath("$.available_start_times[?(@ == '%s')]", scenario.startAt().toInstant())
                .exists());

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "late-local-day")
                .contentType("application/json")
                .content(bookingPayload(scenario)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(availableSlots(scenario))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.available_start_times[?(@ == '%s')]", scenario.startAt().toInstant())
                .doesNotExist());
  }

  @Test
  @Order(2)
  void publicReadsAndBookings_whenRedisStops_shouldContinueFailOpen() throws Exception {
    Scenario scenario = seedScenario();
    redis.stop();

    mockMvc.perform(availableSlots(scenario)).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/v1/public/tenants/{slug}/appointments", scenario.tenantSlug())
                .header("Idempotency-Key", "redis-down")
                .contentType("application/json")
                .content(bookingPayload(scenario)))
        .andExpect(status().isCreated());

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM appointments", Integer.class))
        .isOne();
  }

  @AfterAll
  static void ensureRedisIsStopped() {
    if (redis.isRunning()) {
      redis.stop();
    }
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder availableSlots(
      Scenario scenario) {
    return get("/v1/public/tenants/{slug}/available-slots", scenario.tenantSlug())
        .queryParam("calendar_id", scenario.calendarId().toString())
        .queryParam("offering_id", scenario.offeringId().toString())
        .queryParam("date", scenario.date().toString());
  }

  private Scenario seedScenario() {
    UUID tenantId = UUID.randomUUID();
    UUID collaboratorId = UUID.randomUUID();
    UUID calendarId = UUID.randomUUID();
    UUID offeringId = UUID.randomUUID();
    String tenantSlug = "cache-it-" + tenantId.toString().substring(0, 8);
    LocalDate date = LocalDate.now(ZONE).plusDays(7);
    ZonedDateTime startAt = ZonedDateTime.of(date, LocalTime.of(23, 30), ZONE);

    jdbcTemplate.update(
        "INSERT INTO tenants (id, name, slug, timezone, currency_code) VALUES (?, ?, ?, ?, 'BRL')",
        tenantId,
        "Cache IT",
        tenantSlug,
        ZONE.getId());
    jdbcTemplate.update(
        "INSERT INTO collaborators (id, tenant_id, display_name) VALUES (?, ?, 'Cache Staff')",
        collaboratorId,
        tenantId);
    jdbcTemplate.update(
        """
        INSERT INTO calendars (id, tenant_id, collaborator_id, name, timezone)
        VALUES (?, ?, ?, 'Cache Calendar', ?)
        """,
        calendarId,
        tenantId,
        collaboratorId,
        ZONE.getId());
    jdbcTemplate.update(
        """
        INSERT INTO offerings (id, tenant_id, title, duration_minutes, price_cents)
        VALUES (?, ?, 'Late service', 15, 4500)
        """,
        offeringId,
        tenantId);
    jdbcTemplate.update(
        "INSERT INTO calendar_offerings (tenant_id, calendar_id, offering_id) VALUES (?, ?, ?)",
        tenantId,
        calendarId,
        offeringId);
    jdbcTemplate.update(
        """
        INSERT INTO availability_rules (tenant_id, calendar_id, weekday, start_time, end_time)
        VALUES (?, ?, ?, TIME '22:00', TIME '23:45')
        """,
        tenantId,
        calendarId,
        date.getDayOfWeek().getValue());
    return new Scenario(tenantSlug, calendarId, offeringId, date, startAt);
  }

  private static String bookingPayload(Scenario scenario) {
    return """
        {
          "calendar_id":"%s",
          "offering_id":"%s",
          "start_at":"%s",
          "customer_name":"Cache Customer",
          "customer_phone":"+5585999990019"
        }
        """
        .formatted(
            scenario.calendarId(), scenario.offeringId(), scenario.startAt().toOffsetDateTime());
  }

  private record Scenario(
      String tenantSlug, UUID calendarId, UUID offeringId, LocalDate date, ZonedDateTime startAt) {}
}
