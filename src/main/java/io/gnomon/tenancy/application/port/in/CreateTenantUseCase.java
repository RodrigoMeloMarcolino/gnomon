package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.TenantResult;

public interface CreateTenantUseCase {

  TenantResult create(CreateTenantCommand command);
}
