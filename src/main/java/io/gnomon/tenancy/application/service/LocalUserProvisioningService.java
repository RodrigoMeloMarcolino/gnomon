package io.gnomon.tenancy.application.service;

import io.gnomon.shared.logging.StructuredEventLogger;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserCommand;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserUseCase;
import io.gnomon.tenancy.application.port.in.result.LocalUserResult;
import io.gnomon.tenancy.application.port.out.UserProjectionPort;
import io.gnomon.tenancy.domain.model.User;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class LocalUserProvisioningService implements ProvisionLocalUserUseCase {

  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(LocalUserProvisioningService.class);

  private final UserProjectionPort userProjection;

  public LocalUserProvisioningService(UserProjectionPort userProjection) {
    this.userProjection = userProjection;
  }

  @Override
  @Transactional
  public LocalUserResult provision(ProvisionLocalUserCommand command) {
    Objects.requireNonNull(command, "command");
    LocalUserResult result =
        LocalUserResult.from(
            userProjection.upsert(
                command.keycloakSubject(),
                User.normalizeEmail(command.email()),
                command.displayName()));
    logAfterCommit(
        "auth.user_provisioned", "local user provisioned", Map.of("user.id", result.userId()));
    return result;
  }

  private static void logAfterCommit(String eventName, String message, Map<String, ?> attributes) {
    Runnable log = () -> LOGGER.info(eventName, message, attributes);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              log.run();
            }
          });
      return;
    }
    log.run();
  }
}
