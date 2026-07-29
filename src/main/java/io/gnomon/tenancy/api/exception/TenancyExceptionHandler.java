package io.gnomon.tenancy.api.exception;

import io.gnomon.shared.api.response.ApiErrorResponse;
import io.gnomon.tenancy.domain.exception.TenancyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates stable tenancy failures without coupling shared API code to a feature module. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class TenancyExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(TenancyExceptionHandler.class);

  @ExceptionHandler(TenancyException.class)
  public ResponseEntity<ApiErrorResponse> handle(TenancyException exception) {
    HttpStatus status =
        switch (exception.code()) {
          case "unauthorized" -> HttpStatus.UNAUTHORIZED;
          case "membership_required", "insufficient_role" -> HttpStatus.FORBIDDEN;
          case "tenant_not_found", "user_not_found", "membership_not_found" -> HttpStatus.NOT_FOUND;
          case "tenant_slug_taken", "membership_exists", "last_owner" -> HttpStatus.CONFLICT;
          case "validation_error", "invalid_timezone", "staff_requires_collaborator" ->
              HttpStatus.UNPROCESSABLE_CONTENT;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
      LOGGER.error("Unmapped tenancy error code={}", exception.code());
      return ResponseEntity.status(status)
          .body(ApiErrorResponse.of("internal_server_error", "an unexpected error occurred", null));
    }
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(exception.code(), exception.getMessage(), null));
  }
}
