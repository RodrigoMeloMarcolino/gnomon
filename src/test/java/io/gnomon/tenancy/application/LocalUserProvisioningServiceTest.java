package io.gnomon.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.tenancy.application.port.in.ProvisionLocalUserCommand;
import io.gnomon.tenancy.application.port.in.result.LocalUserResult;
import io.gnomon.tenancy.application.port.out.UserProjectionPort;
import io.gnomon.tenancy.application.service.LocalUserProvisioningService;
import io.gnomon.tenancy.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalUserProvisioningServiceTest {

  @Mock private UserProjectionPort projection;

  private LocalUserProvisioningService service;

  @BeforeEach
  void setUp() {
    service = new LocalUserProvisioningService(projection);
  }

  @Test
  void provision_withMixedCaseEmail_shouldNormalizeBeforeUpsert() {
    User user =
        new User(
            UUID.randomUUID(),
            "keycloak-sub",
            "user@example.com",
            "User",
            Instant.EPOCH,
            Instant.EPOCH);
    when(projection.upsert("keycloak-sub", "user@example.com", "User")).thenReturn(user);

    LocalUserResult result =
        service.provision(
            new ProvisionLocalUserCommand("keycloak-sub", " User@Example.COM ", "User"));

    assertThat(result.email()).isEqualTo("user@example.com");
    verify(projection).upsert("keycloak-sub", "user@example.com", "User");
  }
}
