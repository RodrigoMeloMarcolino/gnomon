package io.gnomon.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GnomonStructuredLogFormatterTest {

  private final GnomonStructuredLogFormatter formatter = new GnomonStructuredLogFormatter();
  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void format_whenStructuredEvent_shouldEmitContractAndRedactRecursively() throws Exception {
    LoggingEvent event = event(Level.INFO, "booking completed");
    event.setMDCPropertyMap(Map.of("request.id", "request-1", "correlation.id", "correlation-1"));
    event.setKeyValuePairs(
        List.of(
            new KeyValuePair("event_name", "appointment.booking_succeeded"),
            new KeyValuePair("tenant.id", "tenant-1"),
            new KeyValuePair("payload", Map.of("access_token", "secret", "safe", "value"))));

    JsonNode result = json.readTree(formatter.format(event));

    assertThat(result.get("timestamp").asString()).isEqualTo("2026-07-31T12:34:56.789Z");
    assertThat(result.get("severity_text").asString()).isEqualTo("INFO");
    assertThat(result.get("severity_number").asInt()).isEqualTo(9);
    assertThat(result.get("body").asString()).isEqualTo("booking completed");
    assertThat(result.get("event_name").asString()).isEqualTo("appointment.booking_succeeded");
    assertThat(result.get("attributes").get("correlation.id").asString())
        .isEqualTo("correlation-1");
    assertThat(result.get("attributes").get("payload").get("access_token").asString())
        .isEqualTo("[REDACTED]");
  }

  @Test
  void format_whenNoEventName_shouldUseApplicationLogFallback() throws Exception {
    LoggingEvent event = event(Level.WARN, "ordinary message");
    event.setMDCPropertyMap(Map.of());
    JsonNode result = json.readTree(formatter.format(event));

    assertThat(result.get("event_name").asString()).isEqualTo("application.log");
    assertThat(result.get("severity_number").asInt()).isEqualTo(13);
  }

  private static LoggingEvent event(Level level, String message) {
    LoggingEvent event = new LoggingEvent();
    event.setLoggerName("io.gnomon.test");
    event.setThreadName("test-thread");
    event.setLevel(level);
    event.setMessage(message);
    event.setInstant(Instant.parse("2026-07-31T12:34:56.789Z"));
    return event;
  }
}
