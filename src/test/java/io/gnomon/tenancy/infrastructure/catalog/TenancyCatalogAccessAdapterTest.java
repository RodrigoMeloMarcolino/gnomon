package io.gnomon.tenancy.infrastructure.integration.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.tenancy.application.port.out.MembershipRepository;
import io.gnomon.tenancy.application.port.out.TenantRepository;
import io.gnomon.tenancy.application.port.out.UserRepository;
import io.gnomon.tenancy.domain.model.Tenant;
import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenancyCatalogAccessAdapterTest {

  @Mock private TenantRepository tenants;
  @Mock private MembershipRepository memberships;
  @Mock private UserRepository users;

  @Test
  void linkStaff_whenUserIsOwner_shouldPreserveOwnerMembership() {
    UUID tenantId = UUID.randomUUID();
    User user =
        new User(
            UUID.randomUUID(),
            "subject",
            "owner@example.com",
            "Owner",
            Instant.EPOCH,
            Instant.EPOCH);
    TenantMembership owner = TenantMembership.owner(tenantId, user.id(), Instant.EPOCH);
    when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
    when(memberships.findByTenantIdAndUserId(tenantId, user.id())).thenReturn(Optional.of(owner));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    adapter.linkStaff(tenantId, user.email(), Instant.EPOCH);

    verify(memberships, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unlinkStaff_whenUserIsAdmin_shouldPreserveMembership() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TenantMembership admin =
        TenantMembership.administrative(
            tenantId, userId, TenantMembership.MembershipRole.ADMIN, Instant.EPOCH);
    when(memberships.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(admin));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    adapter.unlinkStaff(tenantId, userId);

    verify(memberships, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void unlinkStaff_whenUserIsOwner_shouldPreserveMembership() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TenantMembership owner = TenantMembership.owner(tenantId, userId, Instant.EPOCH);
    when(memberships.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(owner));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    adapter.unlinkStaff(tenantId, userId);

    verify(memberships, never()).delete(any());
  }

  @Test
  void unlinkStaff_whenUserIsStaff_shouldDeleteMembership() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TenantMembership staff = TenantMembership.staffForCollaborator(tenantId, userId, Instant.EPOCH);
    when(memberships.findByTenantIdAndUserId(tenantId, userId)).thenReturn(Optional.of(staff));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    adapter.unlinkStaff(tenantId, userId);

    verify(memberships).delete(staff);
  }

  @Test
  void linkStaff_whenMembershipIsInitiallyMissing_shouldUseIdempotentCreation() {
    UUID tenantId = UUID.randomUUID();
    User user =
        new User(
            UUID.randomUUID(),
            "subject",
            "staff@example.com",
            "Staff",
            Instant.EPOCH,
            Instant.EPOCH);
    TenantMembership concurrentlyCreated =
        TenantMembership.staffForCollaborator(tenantId, user.id(), Instant.EPOCH);
    when(users.findByEmail(user.email())).thenReturn(Optional.of(user));
    when(memberships.findByTenantIdAndUserId(tenantId, user.id())).thenReturn(Optional.empty());
    when(memberships.createStaffIfAbsent(any())).thenReturn(concurrentlyCreated);
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    var result = adapter.linkStaff(tenantId, user.email(), Instant.EPOCH);

    assertThat(result.userId()).isEqualTo(user.id());
    assertThat(result.membershipRole()).isEqualTo("staff");
    verify(memberships).createStaffIfAbsent(any());
  }

  @Test
  void requireManager_whenActorIsStaff_shouldReject() {
    UUID userId = UUID.randomUUID();
    Tenant tenant = Tenant.create("Tenant", "tenant", "America/Fortaleza", "BRL", Instant.EPOCH);
    TenantMembership staff =
        TenantMembership.staffForCollaborator(tenant.id(), userId, Instant.EPOCH);
    when(tenants.findBySlug("tenant")).thenReturn(Optional.of(tenant));
    when(memberships.findByTenantIdAndUserId(tenant.id(), userId)).thenReturn(Optional.of(staff));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    assertThatThrownBy(() -> adapter.requireManager(userId, "tenant"))
        .isInstanceOf(CatalogException.class)
        .extracting(exception -> ((CatalogException) exception).code())
        .isEqualTo("insufficient_role");
  }

  @Test
  void requireManager_whenActorIsAdmin_shouldAllow() {
    UUID userId = UUID.randomUUID();
    Tenant tenant = Tenant.create("Tenant", "tenant", "America/Fortaleza", "BRL", Instant.EPOCH);
    TenantMembership admin =
        TenantMembership.administrative(
            tenant.id(), userId, TenantMembership.MembershipRole.ADMIN, Instant.EPOCH);
    when(tenants.findBySlug("tenant")).thenReturn(Optional.of(tenant));
    when(memberships.findByTenantIdAndUserId(tenant.id(), userId)).thenReturn(Optional.of(admin));
    var adapter = new TenancyCatalogAccessAdapter(tenants, memberships, users);

    assertThat(adapter.requireManager(userId, "tenant").actorRole()).isEqualTo("admin");
  }
}
