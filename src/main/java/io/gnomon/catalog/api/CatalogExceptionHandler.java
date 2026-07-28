package io.gnomon.catalog.api;

import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.shared.api.ApiErrorResponse;
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
public class CatalogExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogExceptionHandler.class);

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<ApiErrorResponse> handle(CatalogException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "catalog_access_denied",
              "membership_required",
              "insufficient_role",
              "staff_calendar_mismatch" ->
              HttpStatus.FORBIDDEN;
          case "tenant_not_found",
              "user_not_found",
              "collaborator_not_found",
              "calendar_not_found",
              "offering_not_found" ->
              HttpStatus.NOT_FOUND;
          case "collaborator_already_linked", "calendar_exists" -> HttpStatus.CONFLICT;
          case "validation_error", "invalid_timezone" -> HttpStatus.UNPROCESSABLE_ENTITY;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      LOGGER.error("Unmapped catalog error code={}", exception.code());
      return ResponseEntity.status(status)
          .body(ApiErrorResponse.of("internal_server_error", "an unexpected error occurred", null));
    }
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(exception.code(), exception.getMessage(), null));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> handleIntegrity(DataIntegrityViolationException exception) {
    if (contains(exception, "uq_collaborators_tenant_user")) {
      return error(
          HttpStatus.CONFLICT,
          "collaborator_already_linked",
          "user is already linked to a collaborator");
    }
    if (contains(exception, "uq_calendars_tenant_collaborator")) {
      return error(HttpStatus.CONFLICT, "calendar_exists", "collaborator already has a calendar");
    }
    if (isKnownValidationConstraint(exception)) {
      return error(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "validation_error",
          "catalog data violates a constraint");
    }
    LOGGER.error("Unmapped catalog integrity violation", exception);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error", "an unexpected error occurred");
  }

  private static ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, null));
  }

  private static boolean isKnownValidationConstraint(Throwable exception) {
    return contains(exception, "ck_collaborators_display_name_not_blank")
        || contains(exception, "ck_calendars_name_not_blank")
        || contains(exception, "ck_calendars_timezone_not_blank")
        || contains(exception, "uq_offerings_active_tenant_title")
        || contains(exception, "ck_offerings_title_not_blank")
        || contains(exception, "ck_offerings_duration")
        || contains(exception, "ck_offerings_price")
        || contains(exception, "fk_calendar_offerings_tenant")
        || contains(exception, "fk_calendar_offerings_tenant_calendar")
        || contains(exception, "fk_calendar_offerings_tenant_offering")
        || contains(exception, "pk_calendar_offerings");
  }

  private static boolean contains(Throwable exception, String constraint) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(constraint)) {
        return true;
      }
    }
    return false;
  }
}
