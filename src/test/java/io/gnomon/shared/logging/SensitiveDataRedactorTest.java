package io.gnomon.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SensitiveDataRedactorTest {

  @Test
  void redactsSensitiveFieldsRecursivelyBeforeAppenderDelivery() {
    var redacted =
        SensitiveDataRedactor.redactMap(
            Map.of(
                "idempotency_key", "do-not-export",
                "nested", Map.of("authorization", "Bearer secret"),
                "items", List.of(Map.of("token", "private"), "safe")));

    assertThat(redacted)
        .containsEntry("idempotency_key", "[REDACTED]")
        .containsEntry("nested", Map.of("authorization", "[REDACTED]"))
        .containsEntry("items", List.of(Map.of("token", "[REDACTED]"), "safe"));
  }
}
