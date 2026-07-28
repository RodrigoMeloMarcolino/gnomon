package io.gnomon.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.gnomon.tenancy.api.CreateMembershipRequest;
import io.gnomon.tenancy.api.CreateTenantRequest;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void bodyValidation_whenRequestIsInvalid_shouldReturn422Envelope() {
    var target = new CreateTenantRequest("", "Invalid Slug", "");
    var bindingResult = new BeanPropertyBindingResult(target, "createTenantRequest");
    bindingResult.rejectValue("timezone", "NotBlank", "must not be blank");
    bindingResult.rejectValue("name", "NotBlank", "must not be blank");
    bindingResult.rejectValue("slug", "Pattern", "has an invalid format");

    var response =
        handler.handleBodyValidation(new MethodArgumentNotValidException(null, bindingResult));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("validation_error");
    assertThat(response.getBody().error().message()).isEqualTo("request validation failed");
    assertThat(response.getBody().error().details())
        .isEqualTo(
            List.of(
                new FieldValidationError("name", "must not be blank"),
                new FieldValidationError("slug", "has an invalid format"),
                new FieldValidationError("timezone", "must not be blank")));
  }

  @Test
  void dtoValidation_whenTenantFieldsAreInvalid_shouldReportAllContractFields() {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var violations =
          validatorFactory.getValidator().validate(new CreateTenantRequest("", "Invalid Slug", ""));

      assertThat(violations)
          .extracting(violation -> violation.getPropertyPath().toString())
          .contains("name", "slug", "timezone");
    }
  }

  @Test
  void dtoValidation_whenStaffIsCreatedDirectly_shouldRejectRequest() {
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      var violations =
          validatorFactory
              .getValidator()
              .validate(new CreateMembershipRequest("staff@gnomon.local", "staff"));

      assertThat(violations)
          .singleElement()
          .extracting(violation -> violation.getPropertyPath().toString())
          .isEqualTo("role");
    }
  }

  @Test
  void unexpectedFailure_whenHandlerDoesNotRecognizeException_shouldReturnSafe500() {
    var response = handler.handleUnexpected(new IllegalStateException("sensitive detail"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("internal_server_error");
    assertThat(response.getBody().error().message()).isEqualTo("an unexpected error occurred");
    assertThat(response.getBody().error().details()).isNull();
  }
}
