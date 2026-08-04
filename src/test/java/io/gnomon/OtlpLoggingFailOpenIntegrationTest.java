package io.gnomon;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.gnomon.shared.logging.StructuredEventLogger;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Proves that an unavailable OTLP logs receiver cannot affect HTTP availability. */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class OtlpLoggingFailOpenIntegrationTest {

  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(OtlpLoggingFailOpenIntegrationTest.class);

  @Container @ServiceConnection
  static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  private static final AtomicInteger exportAttempts = new AtomicInteger();
  private static final HttpServer otlpReceiver = startReceiver();

  static {
    System.setProperty("logging.level.root", "WARN");
    System.setProperty("logging.level.io.gnomon", "INFO");
    System.setProperty("otel.instrumentation.logback-appender.enabled", "true");
    System.setProperty("otel.logs.exporter", "otlp");
    System.setProperty(
        "otel.exporter.otlp.logs.endpoint",
        "http://localhost:" + otlpReceiver.getAddress().getPort() + "/v1/logs");
    System.setProperty("otel.exporter.otlp.logs.protocol", "http/protobuf");
    System.setProperty("otel.exporter.otlp.logs.timeout", "100ms");
    System.setProperty("otel.blrp.schedule.delay", "10ms");
    System.setProperty("otel.blrp.max.queue.size", "8");
    System.setProperty("otel.blrp.max.export.batch.size", "1");
  }

  @LocalServerPort int port;

  @Test
  void failingOtlpReceiver_doesNotAffectHealthOrReadiness() {
    LOGGER.info("test.otlp_export", "testing OTLP fail-open", java.util.Map.of());
    awaitExportAttempt();

    given().port(port).when().get("/v1/health").then().statusCode(200);
    given().port(port).when().get("/v1/ready").then().statusCode(200);

    assertThat(exportAttempts.get()).isPositive();
  }

  @AfterAll
  static void stopReceiver() {
    otlpReceiver.stop(0);
    System.clearProperty("logging.level.root");
    System.clearProperty("logging.level.io.gnomon");
    System.clearProperty("otel.instrumentation.logback-appender.enabled");
    System.clearProperty("otel.logs.exporter");
    System.clearProperty("otel.exporter.otlp.logs.endpoint");
    System.clearProperty("otel.exporter.otlp.logs.protocol");
    System.clearProperty("otel.exporter.otlp.logs.timeout");
    System.clearProperty("otel.blrp.schedule.delay");
    System.clearProperty("otel.blrp.max.queue.size");
    System.clearProperty("otel.blrp.max.export.batch.size");
  }

  private static HttpServer startReceiver() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/v1/logs", OtlpLoggingFailOpenIntegrationTest::rejectExport);
      server.start();
      return server;
    } catch (IOException exception) {
      throw new IllegalStateException("unable to start OTLP test receiver", exception);
    }
  }

  private static void rejectExport(HttpExchange exchange) throws IOException {
    try (exchange) {
      exchange.getRequestBody().readAllBytes();
      exportAttempts.incrementAndGet();
      exchange.sendResponseHeaders(503, -1);
    }
  }

  private static void awaitExportAttempt() {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    while (exportAttempts.get() == 0 && Instant.now().isBefore(deadline)) {
      Thread.onSpinWait();
    }
    assertThat(exportAttempts.get()).isPositive();
  }
}
