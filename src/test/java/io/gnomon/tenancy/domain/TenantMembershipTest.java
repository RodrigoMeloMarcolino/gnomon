package io.gnomon.tenancy.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.TenantMembership;
import io.gnomon.tenancy.domain.model.TenantMembership.MembershipRole;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantMembershipTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

  @Test
  void administrative_whenStaffInPhaseOne_shouldReject() {
    assertThatThrownBy(
            () ->
                TenantMembership.administrative(
                    UUID.randomUUID(), UUID.randomUUID(), MembershipRole.STAFF, NOW))
        .isInstanceOf(TenancyException.class)
        .extracting(exception -> ((TenancyException) exception).code())
        .isEqualTo("staff_requires_collaborator");
  }

  @Test
  void changeRole_whenDemotingLastOwner_shouldReject() {
    TenantMembership membership = TenantMembership.owner(UUID.randomUUID(), UUID.randomUUID(), NOW);

    assertThatThrownBy(() -> membership.changeRole(MembershipRole.ADMIN, true, NOW.plusSeconds(1)))
        .isInstanceOf(TenancyException.class)
        .extracting(exception -> ((TenancyException) exception).code())
        .isEqualTo("last_owner");
  }
}
