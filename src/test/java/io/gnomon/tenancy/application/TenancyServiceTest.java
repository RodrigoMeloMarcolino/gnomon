package io.gnomon.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.tenancy.application.port.in.AddMembershipCommand;
import io.gnomon.tenancy.application.port.in.ChangeMembershipRoleCommand;
import io.gnomon.tenancy.application.port.in.CreateTenantCommand;
import io.gnomon.tenancy.application.port.in.result.TenantResult;
import io.gnomon.tenancy.application.port.out.MembershipRepository;
import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.application.port.out.UserRepository;
import io.gnomon.tenancy.application.service.TenancyService;
import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.TenantMembership.MembershipRole;
import io.gnomon.tenancy.domain.model.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenancyServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

  @Mock private TenantRepository tenants;
  @Mock private MembershipRepository memberships;
  @Mock private UserRepository users;

  private TenancyService service;

  @BeforeEach
  void setUp() {
    service = new TenancyService(tenants, memberships, users, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void create_withValidCommand_shouldPersistTenantAndOwnerAtomically() {
    User actor = user("owner@example.com");
    when(users.findById(actor.id())).thenReturn(Optional.of(actor));
    when(tenants.findBySlug("tenant")).thenReturn(Optional.empty());
    when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(memberships.save(any(TenantMembership.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TenantResult result =
        service.create(
            new CreateTenantCommand(actor.id(), "Tenant", "tenant", "America/Fortaleza", null));

    assertThat(result.role()).isEqualTo("owner");
    assertThat(result.currencyCode()).isEqualTo("BRL");
    verify(tenants).save(any(Tenant.class));
    verify(memberships).save(any(TenantMembership.class));
  }

  @Test
  void get_whenActorIsStaff_shouldReject() {
    User actor = user("staff@example.com");
    Tenant tenant = tenant();
    TenantMembership staff = membership(tenant.id(), actor.id(), MembershipRole.STAFF);
    when(tenants.findBySlug(tenant.slug())).thenReturn(Optional.of(tenant));
    when(memberships.findByTenantIdAndUserId(tenant.id(), actor.id()))
        .thenReturn(Optional.of(staff));

    assertCode(() -> service.get(actor.id(), tenant.slug()), "insufficient_role");
  }

  @Test
  void add_whenRoleIsStaffWithoutCollaborator_shouldReject() {
    User actor = user("owner@example.com");
    User target = user("target@example.com");
    Tenant tenant = tenant();
    when(tenants.findBySlug(tenant.slug())).thenReturn(Optional.of(tenant));
    when(memberships.findByTenantIdAndUserId(tenant.id(), actor.id()))
        .thenReturn(Optional.of(membership(tenant.id(), actor.id(), MembershipRole.OWNER)));
    when(users.findByEmail(target.email())).thenReturn(Optional.of(target));
    when(memberships.findByTenantIdAndUserId(tenant.id(), target.id()))
        .thenReturn(Optional.empty());

    assertCode(
        () ->
            service.add(
                new AddMembershipCommand(actor.id(), tenant.slug(), target.email(), "staff")),
        "staff_requires_collaborator");
  }

  @Test
  void changeRole_whenTargetIsLastOwner_shouldReject() {
    User actor = user("owner@example.com");
    Tenant tenant = tenant();
    TenantMembership owner = membership(tenant.id(), actor.id(), MembershipRole.OWNER);
    when(tenants.findBySlug(tenant.slug())).thenReturn(Optional.of(tenant));
    when(memberships.lockByTenantId(tenant.id())).thenReturn(List.of(owner));

    assertCode(
        () ->
            service.changeRole(
                new ChangeMembershipRoleCommand(actor.id(), tenant.slug(), owner.id(), "admin")),
        "last_owner");
  }

  @Test
  void changeRole_whenMembershipBelongsToOtherTenant_shouldReturnForbiddenCode() {
    User actor = user("owner@example.com");
    Tenant tenant = tenant();
    TenantMembership owner = membership(tenant.id(), actor.id(), MembershipRole.OWNER);
    TenantMembership other = membership(UUID.randomUUID(), UUID.randomUUID(), MembershipRole.ADMIN);
    when(tenants.findBySlug(tenant.slug())).thenReturn(Optional.of(tenant));
    when(memberships.lockByTenantId(tenant.id())).thenReturn(List.of(owner));
    when(memberships.findById(other.id())).thenReturn(Optional.of(other));

    assertCode(
        () ->
            service.changeRole(
                new ChangeMembershipRoleCommand(actor.id(), tenant.slug(), other.id(), "admin")),
        "membership_required");
  }

  private static void assertCode(Runnable action, String expectedCode) {
    assertThatThrownBy(action::run)
        .isInstanceOf(TenancyException.class)
        .extracting(exception -> ((TenancyException) exception).code())
        .isEqualTo(expectedCode);
  }

  private static User user(String email) {
    return new User(UUID.randomUUID(), UUID.randomUUID().toString(), email, "User", NOW, NOW);
  }

  private static Tenant tenant() {
    return Tenant.create("Tenant", "tenant", "America/Fortaleza", "BRL", NOW);
  }

  private static TenantMembership membership(UUID tenantId, UUID userId, MembershipRole role) {
    return new TenantMembership(UUID.randomUUID(), tenantId, userId, role, NOW, NOW);
  }
}
