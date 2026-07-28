package io.gnomon.tenancy.application;

import io.gnomon.tenancy.domain.TenantMembership;
import io.gnomon.tenancy.domain.User;
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

  static MembershipResult from(TenantMembership membership, User user) {
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
