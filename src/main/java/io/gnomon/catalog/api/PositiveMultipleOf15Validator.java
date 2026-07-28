package io.gnomon.catalog.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class PositiveMultipleOf15Validator
    implements ConstraintValidator<PositiveMultipleOf15, Integer> {

  @Override
  public boolean isValid(Integer value, ConstraintValidatorContext context) {
    return value == null || (value > 0 && value % 15 == 0);
  }
}
