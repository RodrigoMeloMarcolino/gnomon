package io.gnomon.shared.api;

import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Central translation of HTTP-boundary failures to the stable ADR 0014 envelope. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception) {
    List<FieldValidationError> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
            .sorted(Comparator.comparing(FieldValidationError::field))
            .toList();
    return validationError(details);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiErrorResponse> handleMethodValidation(
      HandlerMethodValidationException exception) {
    List<FieldValidationError> details =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldValidationError(
                                    result.getMethodParameter().getParameterName(),
                                    error.getDefaultMessage())))
            .sorted(Comparator.comparing(FieldValidationError::field))
            .toList();
    return validationError(details);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException exception) {
    return validationError(
        List.of(
            new FieldValidationError(
                exception.getHeaderName(), "required request header is missing")));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ApiErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException exception) {
    return validationError(
        List.of(new FieldValidationError(exception.getName(), "has an invalid value")));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
    return validationError(List.of(new FieldValidationError("body", "request body is invalid")));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
    LOGGER.error("Unhandled API exception type={}", exception.getClass().getName());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiErrorResponse.of("internal_server_error", "an unexpected error occurred", null));
  }

  private static ResponseEntity<ApiErrorResponse> validationError(
      List<FieldValidationError> details) {
    return ResponseEntity.unprocessableEntity()
        .body(ApiErrorResponse.of("validation_error", "request validation failed", details));
  }
}
