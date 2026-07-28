package io.gnomon.tenancy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class TenantMembership {

  private final UUID id;
  private final UUID tenantId;
  private final UUID userId;
  private MembershipRole role;
  private final Instant createdAt;
  private Instant updatedAt;

  public TenantMembership(
      UUID id,
      UUID tenantId,
      UUID userId,
      MembershipRole role,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.role = Objects.requireNonNull(role, "role");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static TenantMembership owner(UUID tenantId, UUID userId, Instant now) {
    return new TenantMembership(
        UUID.randomUUID(), tenantId, userId, MembershipRole.OWNER, now, now);
  }

  public static TenantMembership administrative(
      UUID tenantId, UUID userId, MembershipRole role, Instant now) {
    if (role == MembershipRole.STAFF) {
      throw new TenancyException(
          "staff_requires_collaborator", "staff membership requires a linked collaborator");
    }
    return new TenantMembership(UUID.randomUUID(), tenantId, userId, role, now, now);
  }

  public void changeRole(MembershipRole newRole, boolean lastOwner, Instant now) {
    Objects.requireNonNull(newRole, "newRole");
    if (newRole == MembershipRole.STAFF) {
      throw new TenancyException(
          "staff_requires_collaborator", "staff membership requires a linked collaborator");
    }
    if (role == MembershipRole.OWNER && newRole != MembershipRole.OWNER && lastOwner) {
      throw new TenancyException("last_owner", "the last owner cannot be demoted");
    }
    role = newRole;
    updatedAt = Objects.requireNonNull(now, "now");
  }

  public boolean isLastOwnerRemoval(long ownerCount) {
    return role == MembershipRole.OWNER && ownerCount <= 1;
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID userId() {
    return userId;
  }

  public MembershipRole role() {
    return role;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public enum MembershipRole {
    OWNER,
    ADMIN,
    STAFF;

    public static MembershipRole from(String value) {
      if (value == null) {
        throw new TenancyException("validation_error", "membership role is required");
      }
      try {
        return valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
        throw new TenancyException("validation_error", "membership role is invalid");
      }
    }

    public boolean canReadTenant() {
      return this == OWNER || this == ADMIN;
    }

    public boolean canManageTenant() {
      return this == OWNER;
    }

    public boolean canReadMemberships() {
      return this == OWNER || this == ADMIN;
    }

    public String databaseValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
