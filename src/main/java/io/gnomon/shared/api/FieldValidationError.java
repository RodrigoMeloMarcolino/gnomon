package io.gnomon.shared.api;

/** Safe, field-oriented validation detail returned to API consumers. */
public record FieldValidationError(String field, String message) {}
