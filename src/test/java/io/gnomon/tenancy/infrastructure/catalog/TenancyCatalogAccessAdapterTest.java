package io.gnomon.tenancy.infrastructure.catalog;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.tenancy.application.port.MembershipRepository;
import io.gnomon.tenancy.application.port.TenantRepository;
import io.gnomon.tenancy.application.port.UserRepository;
import io.gnomon.tenancy.domain.TenantMembership;
import io.gnomon.tenancy.domain.User;
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
}
