package io.gnomon.catalog.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = PositiveMultipleOf15Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveMultipleOf15 {

  String message() default "must be a positive multiple of 15";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
