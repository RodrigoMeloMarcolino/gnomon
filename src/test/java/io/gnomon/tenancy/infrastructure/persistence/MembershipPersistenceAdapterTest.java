package io.gnomon.tenancy.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.tenancy.domain.TenantMembership;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MembershipPersistenceAdapterTest {

  @Mock private SpringDataMembershipRepository repository;

  @Test
  void createStaffIfAbsent_whenConcurrentMembershipExists_shouldReturnPersistedMembership() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    TenantMembership requested =
        TenantMembership.staffForCollaborator(tenantId, userId, Instant.EPOCH);
    TenantMembership persisted =
        TenantMembership.staffForCollaborator(tenantId, userId, Instant.EPOCH);
    when(repository.findByTenantIdAndUserId(tenantId, userId))
        .thenReturn(Optional.of(MembershipJpaEntity.from(persisted)));
    var adapter = new MembershipPersistenceAdapter(repository);

    TenantMembership result = adapter.createStaffIfAbsent(requested);

    assertThat(result.id()).isEqualTo(persisted.id());
    verify(repository)
        .insertStaffIfAbsent(
            requested.id(), tenantId, userId, requested.createdAt(), requested.updatedAt());
  }
}
