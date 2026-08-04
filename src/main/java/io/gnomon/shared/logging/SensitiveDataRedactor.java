package io.gnomon.shared.logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Redacts known sensitive fields before they reach any logging appender. */
public final class SensitiveDataRedactor {

  private static final Set<String> SENSITIVE_SEGMENTS =
      Set.of("password", "token", "authorization", "key", "secret");

  private SensitiveDataRedactor() {}

  public static Map<String, Object> redactMap(Map<String, ?> source) {
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> result.put(key, isSensitive(key) ? "[REDACTED]" : redact(value)));
    return result;
  }

  public static Object redact(Object value) {
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
      return collection.stream().map(SensitiveDataRedactor::redact).toList();
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
}
