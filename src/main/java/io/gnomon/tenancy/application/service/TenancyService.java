package io.gnomon.tenancy.application.service;

import io.gnomon.tenancy.application.port.in.CreateTenantUseCase;
import io.gnomon.tenancy.application.port.in.CreateTenantUseCase.CreateTenantCommand;
import io.gnomon.tenancy.application.port.in.GetTenantUseCase;
import io.gnomon.tenancy.application.port.in.ListMyTenantsUseCase;
import io.gnomon.tenancy.application.port.in.ManageMembershipUseCase;
import io.gnomon.tenancy.application.port.in.ManageMembershipUseCase.AddMembershipCommand;
import io.gnomon.tenancy.application.port.in.ManageMembershipUseCase.ChangeMembershipRoleCommand;
import io.gnomon.tenancy.application.port.in.ManageMembershipUseCase.RemoveMembershipCommand;
import io.gnomon.tenancy.application.port.in.UpdateTenantUseCase;
import io.gnomon.tenancy.application.port.in.UpdateTenantUseCase.UpdateTenantCommand;
import io.gnomon.tenancy.application.port.in.result.MembershipResult;
import io.gnomon.tenancy.application.port.in.result.TenantResult;
import io.gnomon.tenancy.application.port.out.MembershipRepository;
import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.application.port.out.UserRepository;
import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.TenantMembership.MembershipRole;
import io.gnomon.tenancy.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenancyService
    implements CreateTenantUseCase,
        ListMyTenantsUseCase,
        GetTenantUseCase,
        UpdateTenantUseCase,
        ManageMembershipUseCase {

  private final TenantRepository tenants;
  private final MembershipRepository memberships;
  private final UserRepository users;
  private final Clock clock;

  @Autowired
  public TenancyService(
      TenantRepository tenants, MembershipRepository memberships, UserRepository users) {
    this(tenants, memberships, users, Clock.systemUTC());
  }

  public TenancyService(
      TenantRepository tenants,
      MembershipRepository memberships,
      UserRepository users,
      Clock clock) {
    this.tenants = tenants;
    this.memberships = memberships;
    this.users = users;
    this.clock = clock;
  }

  @Override
  @Transactional
  public TenantResult create(CreateTenantCommand command) {
    Objects.requireNonNull(command, "command");
    requireUser(command.actorUserId());
    tenants
        .findBySlug(command.slug())
        .ifPresent(
            ignored -> {
              throw new TenancyException("tenant_slug_taken", "tenant slug is already in use");
            });

    Instant now = clock.instant();
    Tenant tenant =
        tenants.save(
            Tenant.create(
                command.name(), command.slug(), command.timezone(), command.currencyCode(), now));
    TenantMembership owner =
        memberships.save(TenantMembership.owner(tenant.id(), command.actorUserId(), now));
    return TenantResult.from(tenant, owner);
  }

  @Override
  public List<TenantResult> list(UUID actorUserId) {
    requireUser(actorUserId);
    Map<UUID, TenantMembership> membershipsByTenant =
        memberships.findByUserId(actorUserId).stream()
            .collect(Collectors.toMap(TenantMembership::tenantId, Function.identity()));
    return tenants.findByMemberUserId(actorUserId).stream()
        .map(tenant -> TenantResult.from(tenant, membershipsByTenant.get(tenant.id())))
        .toList();
  }

  @Override
  public TenantResult get(UUID actorUserId, String tenantSlug) {
    Tenant tenant = requireTenant(tenantSlug);
    TenantMembership membership = requireMembership(tenant.id(), actorUserId);
    if (!membership.role().canReadTenant()) {
      throw new TenancyException("insufficient_role", "owner or admin role is required");
    }
    return TenantResult.from(tenant, membership);
  }

  @Override
  @Transactional
  public TenantResult update(UpdateTenantCommand command) {
    Objects.requireNonNull(command, "command");
    Tenant tenant = requireTenant(command.tenantSlug());
    TenantMembership membership = requireMembership(tenant.id(), command.actorUserId());
    if (!membership.role().canManageTenant()) {
      throw new TenancyException("insufficient_role", "owner role is required");
    }
    tenant.update(
        command.name(),
        command.timezone(),
        command.currencyCode(),
        Tenant.TenantStatus.from(command.status()),
        clock.instant());
    return TenantResult.from(tenants.save(tenant), membership);
  }

  @Override
  public List<MembershipResult> list(UUID actorUserId, String tenantSlug) {
    Tenant tenant = requireTenant(tenantSlug);
    TenantMembership actorMembership = requireMembership(tenant.id(), actorUserId);
    if (!actorMembership.role().canReadMemberships()) {
      throw new TenancyException("insufficient_role", "owner or admin role is required");
    }
    List<TenantMembership> tenantMemberships = memberships.findByTenantId(tenant.id());
    Map<UUID, User> usersById =
        users.findByIdIn(tenantMemberships.stream().map(TenantMembership::userId).toList()).stream()
            .collect(Collectors.toMap(User::id, Function.identity()));
    return tenantMemberships.stream()
        .map(membership -> MembershipResult.from(membership, usersById.get(membership.userId())))
        .toList();
  }

  @Override
  @Transactional
  public MembershipResult add(AddMembershipCommand command) {
    Objects.requireNonNull(command, "command");
    Tenant tenant = requireTenant(command.tenantSlug());
    requireOwner(tenant.id(), command.actorUserId());
    MembershipRole role = MembershipRole.from(command.role());
    User user =
        users
            .findByEmail(User.normalizeEmail(command.userEmail()))
            .orElseThrow(() -> new TenancyException("user_not_found", "user was not found"));
    memberships
        .findByTenantIdAndUserId(tenant.id(), user.id())
        .ifPresent(
            ignored -> {
              throw new TenancyException("membership_exists", "membership already exists");
            });
    TenantMembership membership =
        memberships.save(
            TenantMembership.administrative(tenant.id(), user.id(), role, clock.instant()));
    return MembershipResult.from(membership, user);
  }

  @Override
  @Transactional
  public MembershipResult changeRole(ChangeMembershipRoleCommand command) {
    Objects.requireNonNull(command, "command");
    Tenant tenant = requireTenant(command.tenantSlug());
    List<TenantMembership> locked = memberships.lockByTenantId(tenant.id());
    requireOwner(locked, command.actorUserId());
    TenantMembership membership = membershipInTenant(command.membershipId(), tenant.id(), locked);
    MembershipRole newRole = MembershipRole.from(command.role());
    long ownerCount = locked.stream().filter(item -> item.role() == MembershipRole.OWNER).count();
    membership.changeRole(newRole, ownerCount <= 1, clock.instant());
    User user = requireUser(membership.userId());
    return MembershipResult.from(memberships.save(membership), user);
  }

  @Override
  @Transactional
  public void remove(RemoveMembershipCommand command) {
    Objects.requireNonNull(command, "command");
    Tenant tenant = requireTenant(command.tenantSlug());
    List<TenantMembership> locked = memberships.lockByTenantId(tenant.id());
    requireOwner(locked, command.actorUserId());
    TenantMembership membership = membershipInTenant(command.membershipId(), tenant.id(), locked);
    long ownerCount = locked.stream().filter(item -> item.role() == MembershipRole.OWNER).count();
    if (membership.isLastOwnerRemoval(ownerCount)) {
      throw new TenancyException("last_owner", "the last owner cannot be removed");
    }
    memberships.delete(membership);
  }

  private Tenant requireTenant(String slug) {
    return tenants
        .findBySlug(slug)
        .orElseThrow(() -> new TenancyException("tenant_not_found", "tenant was not found"));
  }

  private User requireUser(UUID userId) {
    if (userId == null) {
      throw new TenancyException("unauthorized", "local user is required");
    }
    return users
        .findById(userId)
        .orElseThrow(() -> new TenancyException("unauthorized", "local user is required"));
  }

  private TenantMembership requireMembership(UUID tenantId, UUID userId) {
    return memberships
        .findByTenantIdAndUserId(tenantId, userId)
        .orElseThrow(
            () ->
                new TenancyException(
                    "membership_required", "membership is required for this tenant"));
  }

  private void requireOwner(UUID tenantId, UUID actorUserId) {
    TenantMembership actor = requireMembership(tenantId, actorUserId);
    if (!actor.role().canManageTenant()) {
      throw new TenancyException("insufficient_role", "owner role is required");
    }
  }

  private static void requireOwner(List<TenantMembership> locked, UUID actorUserId) {
    TenantMembership actor =
        locked.stream()
            .filter(item -> item.userId().equals(actorUserId))
            .findFirst()
            .orElseThrow(
                () ->
                    new TenancyException(
                        "membership_required", "membership is required for this tenant"));
    if (!actor.role().canManageTenant()) {
      throw new TenancyException("insufficient_role", "owner role is required");
    }
  }

  private TenantMembership membershipInTenant(
      UUID membershipId, UUID tenantId, List<TenantMembership> locked) {
    return locked.stream()
        .filter(item -> item.id().equals(membershipId))
        .findFirst()
        .orElseGet(
            () -> {
              if (memberships.findById(membershipId).isPresent()) {
                throw new TenancyException(
                    "membership_required", "cross-tenant access is forbidden");
              }
              throw new TenancyException("membership_not_found", "membership was not found");
            });
  }
}
