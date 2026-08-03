package io.gnomon.shared.logging;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small application-facing logging facade. It keeps Logback and OpenTelemetry details out of
 * feature modules while retaining SLF4J as the logging API mandated by ADR 0015.
 */
public final class StructuredEventLogger {

  private final Logger logger;

  private StructuredEventLogger(Class<?> source) {
    this.logger = LoggerFactory.getLogger(source);
  }

  public static StructuredEventLogger getLogger(Class<?> source) {
    return new StructuredEventLogger(source);
  }

  public void info(String eventName, String body, Map<String, ?> attributes) {
    log(Level.INFO, eventName, body, attributes, null);
  }

  public void warn(String eventName, String body, Map<String, ?> attributes) {
    log(Level.WARN, eventName, body, attributes, null);
  }

  public void error(String eventName, String body, Map<String, ?> attributes, Throwable error) {
    log(Level.ERROR, eventName, body, attributes, error);
  }

  private void log(
      Level level, String eventName, String body, Map<String, ?> attributes, Throwable error) {
    var builder =
        switch (level) {
          case INFO -> logger.atInfo();
          case WARN -> logger.atWarn();
          case ERROR -> logger.atError();
        };
    builder.addKeyValue("event_name", eventName);
    SensitiveDataRedactor.redactMap(attributes).forEach(builder::addKeyValue);
    if (error != null) {
      builder.setCause(error);
    }
    builder.log(body);
  }

  private enum Level {
    INFO,
    WARN,
    ERROR
  }
}
