package io.gnomon.booking.api.exception;

import io.gnomon.booking.application.exception.BookingErrorCodes;
import io.gnomon.booking.application.exception.BookingException;
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
public class BookingExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(BookingExceptionHandler.class);

  @ExceptionHandler(BookingException.class)
  ResponseEntity<ApiErrorResponse> handle(BookingException exception) {
    if (BookingErrorCodes.SLOT_UNAVAILABLE_VALIDATION.equals(exception.code())) {
      return error(HttpStatus.UNPROCESSABLE_CONTENT, "slot_unavailable", exception.getMessage());
    }
    HttpStatus status =
        switch (exception.code()) {
          case "tenant_not_found", "calendar_not_found", "offering_not_found" ->
              HttpStatus.NOT_FOUND;
          case "slot_unavailable", "idempotency_key_conflict" -> HttpStatus.CONFLICT;
          case "phone_invalid", "validation_error" -> HttpStatus.UNPROCESSABLE_CONTENT;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      LOGGER.error("Unmapped booking error code={}", exception.code());
      return error(status, "internal_server_error", "an unexpected error occurred");
    }
    return error(status, exception.code(), exception.getMessage());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception) {
    if (contains(exception, "pk_appointment_slots")) {
      return error(HttpStatus.CONFLICT, "slot_unavailable", "requested slot is unavailable");
    }
    if (contains(exception, "uq_appointments_tenant_idempotency_key")) {
      return error(
          HttpStatus.CONFLICT,
          "idempotency_key_conflict",
          "Idempotency-Key was already used for another request");
    }
    if (isKnownValidationConstraint(exception)) {
      return error(
          HttpStatus.UNPROCESSABLE_CONTENT,
          "validation_error",
          "booking data violates a constraint");
    }
    LOGGER.error("Unmapped booking integrity violation", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "an unexpected error occurred");
  }

  private static boolean isKnownValidationConstraint(Throwable exception) {
    return contains(exception, "uq_appointments_tenant_identity")
        || contains(exception, "ck_appointments_time_order")
        || contains(exception, "ck_appointments_duration_snapshot")
        || contains(exception, "ck_appointments_timezone_not_blank")
        || contains(exception, "ck_appointments_status")
        || contains(exception, "ck_appointments_idempotency_key_not_blank")
        || contains(exception, "ck_appointments_idempotency_fingerprint")
        || contains(exception, "fk_appointments_tenant")
        || contains(exception, "fk_appointments_tenant_calendar")
        || contains(exception, "fk_appointments_tenant_offering")
        || contains(exception, "fk_appointments_customer")
        || contains(exception, "fk_appointment_slots_tenant")
        || contains(exception, "fk_appointment_slots_tenant_appointment")
        || contains(exception, "fk_appointment_slots_tenant_calendar");
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
