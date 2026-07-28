package io.gnomon.tenancy.application;

import io.gnomon.tenancy.application.port.UserProjectionPort;
import io.gnomon.tenancy.domain.User;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalUserProvisioningService implements ProvisionLocalUserUseCase {

  private final UserProjectionPort userProjection;

  public LocalUserProvisioningService(UserProjectionPort userProjection) {
    this.userProjection = userProjection;
  }

  @Override
  @Transactional
  public LocalUserResult provision(ProvisionLocalUserCommand command) {
    Objects.requireNonNull(command, "command");
    return LocalUserResult.from(
        userProjection.upsert(
            command.keycloakSubject(),
            User.normalizeEmail(command.email()),
            command.displayName()));
  }
}
