package io.gnomon.tenancy.application;

public interface ProvisionLocalUserUseCase {

  LocalUserResult provision(ProvisionLocalUserCommand command);

  record ProvisionLocalUserCommand(String keycloakSubject, String email, String displayName) {}
}
