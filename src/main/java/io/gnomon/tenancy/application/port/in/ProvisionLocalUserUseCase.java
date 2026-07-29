package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.LocalUserResult;

public interface ProvisionLocalUserUseCase {

  LocalUserResult provision(ProvisionLocalUserCommand command);
}
