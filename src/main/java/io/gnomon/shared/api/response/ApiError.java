package io.gnomon.shared.api.response;

/**
 * Stable error body defined by ADR 0014.
 *
 * @param code machine-readable error code
 * @param message safe human-readable message
 * @param details optional structured details, normally field validation errors
 */
public record ApiError(String code, String message, Object details) {}
