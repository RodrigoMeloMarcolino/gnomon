package io.gnomon.customers.api.exception;

import io.gnomon.customers.domain.exception.CustomerException;
import io.gnomon.shared.api.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class CustomerExceptionHandler {
  @ExceptionHandler(CustomerException.class)
  ResponseEntity<ApiErrorResponse> handle(CustomerException ex) {
    HttpStatus status =
        switch (ex.code()) {
          case "customer_not_found", "tenant_not_found" -> HttpStatus.NOT_FOUND;
          case "customer_access_denied",
              "insufficient_role",
              "catalog_access_denied",
              "membership_required" ->
              HttpStatus.FORBIDDEN;
          case "validation_error" -> HttpStatus.UNPROCESSABLE_CONTENT;
          default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    return ResponseEntity.status(status)
        .body(
            ApiErrorResponse.of(
                status == HttpStatus.INTERNAL_SERVER_ERROR ? "internal_server_error" : ex.code(),
                status == HttpStatus.INTERNAL_SERVER_ERROR
                    ? "an unexpected error occurred"
                    : ex.getMessage(),
                null));
  }
}
