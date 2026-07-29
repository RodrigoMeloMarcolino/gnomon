package io.gnomon.tenancy.api.response;

import io.gnomon.tenancy.application.port.in.result.TenantResult;
import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String name,
    String slug,
    String timezone,
    String currencyCode,
    String status,
    String role,
    Instant createdAt,
    Instant updatedAt) {

  public static TenantResponse from(TenantResult result) {
    return new TenantResponse(
        result.id(),
        result.name(),
        result.slug(),
        result.timezone(),
        result.currencyCode(),
        result.status(),
        result.role(),
        result.createdAt(),
        result.updatedAt());
  }
}
