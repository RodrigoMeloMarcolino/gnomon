package io.gnomon.shared.api.response;

/** Safe, field-oriented validation detail returned to API consumers. */
public record FieldValidationError(String field, String message) {}
