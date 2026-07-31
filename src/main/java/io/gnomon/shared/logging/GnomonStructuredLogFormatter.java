package io.gnomon.shared.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import org.springframework.core.env.Environment;

/** JSON Lines formatter for the stable Gnomon event contract. */
public final class GnomonStructuredLogFormatter implements StructuredLogFormatter<ILoggingEvent> {

  private static final Set<String> SENSITIVE_SEGMENTS =
      Set.of("password", "token", "authorization", "key", "secret");

  private final String serviceName;

  public GnomonStructuredLogFormatter() {
    this.serviceName = environmentServiceName();
  }

  public GnomonStructuredLogFormatter(Environment environment) {
    this.serviceName = environment.getProperty("otel.service.name", environmentServiceName());
  }

  @Override
  public String format(ILoggingEvent event) {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("logger", event.getLoggerName());
    attributes.put("thread", event.getThreadName());
    event.getMDCPropertyMap().forEach(attributes::put);
    List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
    if (keyValuePairs != null) {
      for (KeyValuePair pair : keyValuePairs) {
        if (!"event_name".equals(pair.key)) {
          attributes.put(pair.key, pair.value);
        }
      }
    }

    String eventName = eventName(event);
    Map<String, Object> document = new LinkedHashMap<>();
    document.put(
        "timestamp",
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(event.getTimeStamp())));
    document.put("severity_text", event.getLevel().levelStr);
    document.put("severity_number", severityNumber(event.getLevel()));
    document.put("body", event.getFormattedMessage());
    document.put("event_name", eventName);
    document.put("service.name", serviceName);
    String traceId = event.getMDCPropertyMap().get("trace_id");
    String spanId = event.getMDCPropertyMap().get("span_id");
    if (traceId != null) {
      document.put("trace_id", traceId);
    }
    if (spanId != null) {
      document.put("span_id", spanId);
    }
    document.put("attributes", redactMap(attributes));
    if (event.getThrowableProxy() != null) {
      document.put("exception.type", event.getThrowableProxy().getClassName());
      document.put("exception.message", event.getThrowableProxy().getMessage());
      document.put("exception.stacktrace", ThrowableProxyUtil.asString(event.getThrowableProxy()));
    }
    return json(document);
  }

  private static String eventName(ILoggingEvent event) {
    List<KeyValuePair> keyValuePairs = event.getKeyValuePairs();
    if (keyValuePairs != null) {
      for (KeyValuePair pair : keyValuePairs) {
        if ("event_name".equals(pair.key) && pair.value != null) {
          return String.valueOf(pair.value);
        }
      }
    }
    return "application.log";
  }

  private static int severityNumber(Level level) {
    return switch (level.toInt()) {
      case Level.TRACE_INT -> 1;
      case Level.DEBUG_INT -> 5;
      case Level.INFO_INT -> 9;
      case Level.WARN_INT -> 13;
      default -> 17;
    };
  }

  private static String environmentServiceName() {
    String value = System.getenv("OTEL_SERVICE_NAME");
    return value == null || value.isBlank() ? "gnomon" : value;
  }

  private static Map<String, Object> redactMap(Map<String, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> result.put(key, isSensitive(key) ? "[REDACTED]" : redact(value)));
    return result;
  }

  private static Object redact(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach(
          (key, nestedValue) -> {
            String nestedKey = String.valueOf(key);
            result.put(nestedKey, isSensitive(nestedKey) ? "[REDACTED]" : redact(nestedValue));
          });
      return result;
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(GnomonStructuredLogFormatter::redact).toList();
    }
    if (value != null && value.getClass().isArray()) {
      List<Object> values = new ArrayList<>();
      int length = java.lang.reflect.Array.getLength(value);
      for (int index = 0; index < length; index++) {
        values.add(redact(java.lang.reflect.Array.get(value, index)));
      }
      return values;
    }
    return value;
  }

  private static boolean isSensitive(String key) {
    for (String segment : key.toLowerCase().split("[._-]")) {
      if (SENSITIVE_SEGMENTS.contains(segment)) {
        return true;
      }
    }
    return false;
  }

  private static String json(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    if (value instanceof Map<?, ?> map) {
      return map.entrySet().stream()
          .map(entry -> quote(String.valueOf(entry.getKey())) + ":" + json(entry.getValue()))
          .reduce((left, right) -> left + "," + right)
          .map(joined -> "{" + joined + "}")
          .orElse("{}");
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream()
          .map(GnomonStructuredLogFormatter::json)
          .reduce((left, right) -> left + "," + right)
          .map(joined -> "[" + joined + "]")
          .orElse("[]");
    }
    return quote(String.valueOf(value));
  }

  private static String quote(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.append('"').toString();
  }
}
