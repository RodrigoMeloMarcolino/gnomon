package io.gnomon.tenancy.api;

import io.gnomon.tenancy.application.MembershipResult;
import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
    UUID id,
    UUID tenantId,
    UUID userId,
    String email,
    String displayName,
    String role,
    Instant createdAt,
    Instant updatedAt) {

  public static MembershipResponse from(MembershipResult result) {
    return new MembershipResponse(
        result.id(),
        result.tenantId(),
        result.userId(),
        result.email(),
        result.displayName(),
        result.role(),
        result.createdAt(),
        result.updatedAt());
  }
}
