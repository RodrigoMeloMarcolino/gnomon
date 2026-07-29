package io.gnomon.tenancy.application.port.in;

import io.gnomon.tenancy.application.port.in.result.TenantResult;

public interface UpdateTenantUseCase {

  TenantResult update(UpdateTenantCommand command);
}
