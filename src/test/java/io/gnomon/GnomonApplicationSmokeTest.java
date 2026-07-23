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
}
