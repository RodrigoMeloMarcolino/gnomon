package io.gnomon.tenancy.application.port.in.result;

import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.User;
import java.time.Instant;
import java.util.UUID;

public record MembershipResult(
    UUID id,
    UUID tenantId,
    UUID userId,
    String email,
    String displayName,
    String role,
    Instant createdAt,
    Instant updatedAt) {

  public static MembershipResult from(TenantMembership membership, User user) {
    return new MembershipResult(
        membership.id(),
        membership.tenantId(),
        membership.userId(),
        user.email(),
        user.displayName(),
        membership.role().databaseValue(),
        membership.createdAt(),
        membership.updatedAt());
  }
}
