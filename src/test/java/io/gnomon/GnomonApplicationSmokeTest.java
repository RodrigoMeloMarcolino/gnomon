package io.gnomon;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Smoke de contexto da fundação: sobe a aplicação completa contra um PostgreSQL efêmero
 * (Testcontainers — nenhum container local de desenvolvimento é reutilizado ou alterado) e valida
 * health/readiness e a migration de extensões.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class GnomonApplicationSmokeTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @LocalServerPort int port;

  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUpRestAssured() {
    RestAssured.port = port;
  }

  @Test
  void healthReturnsOk() {
    given().when().get("/v1/health").then().statusCode(200).body("status", equalTo("ok"));
  }

  @Test
  void readyValidatesPostgreSQL() {
    given().when().get("/v1/ready").then().statusCode(200).body("status", equalTo("ready"));
  }

  @Test
  void flywayAppliedBaseExtensions() {
    Integer extensions =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_extension WHERE extname IN ('pgcrypto', 'citext')",
            Integer.class);

    assertThat(extensions).isEqualTo(2);
  }

  @Test
  void openApiDocumentsUmbraContractSemantically() throws Exception {
    JsonMapper mapper = JsonMapper.builder().build();
    String json = given().when().get("/v3/api-docs").then().statusCode(200).extract().asString();
    JsonNode document = mapper.readTree(json);
    JsonNode paths = document.path("paths");

    assertThat(paths.has("/v1/public/tenants/{tenantSlug}")).isTrue();
    assertThat(paths.has("/v1/public/tenants/{tenantSlug}/calendars")).isTrue();
    assertThat(paths.has("/v1/public/tenants/{tenantSlug}/offerings")).isTrue();
    assertThat(paths.has("/v1/public/tenants/{tenantSlug}/available-slots")).isTrue();
    assertThat(paths.has("/v1/public/tenants/{tenantSlug}/appointments")).isTrue();
    assertThat(paths.has("/v1/tenants")).isTrue();
    assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
    assertThat(paths.path("/v1/tenants").path("get").path("security").isArray()).isTrue();
    assertThat(
            paths
                .path("/v1/public/tenants/{tenantSlug}")
                .path("get")
                .path("security")
                .isMissingNode())
        .isTrue();

    JsonNode booking = paths.path("/v1/public/tenants/{tenantSlug}/appointments").path("post");
    assertThat(booking.path("responses").has("201")).isTrue();
    assertThat(booking.path("responses").has("200")).isTrue();
    assertThat(booking.path("responses").has("409")).isTrue();
    assertThat(booking.path("responses").has("422")).isTrue();
    assertThat(booking.toString()).contains("90000000-0000-4000-8000-000000000001");

    assertThat(paths.path("/v1/public/tenants/{tenantSlug}/available-slots").path("get").toString())
        .contains("calendar_id", "offering_id", "date");
    JsonNode schemas = document.path("components").path("schemas");
    assertThat(
            schemas.path("AvailableSlotsResponse").path("properties").has("available_start_times"))
        .isTrue();
    assertThat(schemas.path("AvailableSlotsResponse").path("properties").has("availableStartTimes"))
        .isFalse();
    assertThat(schemas.path("CreateAppointmentRequest").path("properties").has("calendar_id"))
        .isTrue();
    assertThat(schemas.path("CreateAppointmentRequest").path("properties").has("offering_id"))
        .isTrue();
    assertThat(schemas.path("CreateAppointmentRequest").path("properties").has("customer_name"))
        .isTrue();
    assertThat(schemas.path("CreateAppointmentRequest").path("properties").has("calendarId"))
        .isFalse();
    assertThat(schemas.path("TenantSelectionResponse").path("properties").has("currency_code"))
        .isTrue();
    assertThat(schemas.path("TenantSelectionResponse").path("properties").has("currencyCode"))
        .isFalse();
    assertThat(document.toString()).contains("ApiErrorResponse");

    String yaml =
        given().when().get("/v3/api-docs.yaml").then().statusCode(200).extract().asString();
    assertThat(yaml).contains("/v1/public/tenants/{tenantSlug}/appointments", "bearerAuth");
  }
}
