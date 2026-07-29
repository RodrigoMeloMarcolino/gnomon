package io.gnomon.availability.api.exception;

import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.shared.api.response.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class AvailabilityExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(AvailabilityExceptionHandler.class);

  @ExceptionHandler(AvailabilityException.class)
  ResponseEntity<ApiErrorResponse> handle(AvailabilityException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "availability_access_denied", "staff_calendar_mismatch" -> HttpStatus.FORBIDDEN;
          case "calendar_not_found", "availability_rule_not_found" -> HttpStatus.NOT_FOUND;
          case "validation_error" -> HttpStatus.UNPROCESSABLE_CONTENT;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      LOGGER.error("Unmapped availability error code={}", exception.code());
      return error(status, "internal_server_error", "an unexpected error occurred");
    }
    return error(status, exception.code(), exception.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception) {
    if (isKnownConstraint(exception)) {
      return error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "validation_error",
          "availability rule violates a constraint");
    }
    LOGGER.error("Unmapped availability integrity violation", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "an unexpected error occurred");
  }

  private static boolean isKnownConstraint(Throwable exception) {
    return contains(exception, "ck_availability_rules_weekday")
        || contains(exception, "ck_availability_rules_time_order")
        || contains(exception, "ck_availability_rules_time_alignment")
        || contains(exception, "fk_availability_rules_tenant")
        || contains(exception, "fk_availability_rules_tenant_calendar");
  }

  private static boolean contains(Throwable exception, String value) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(value)) {
        return true;
      }
    }
    return false;
  }

  private static ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, null));
  }
}
