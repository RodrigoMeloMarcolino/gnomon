package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;

public interface UpdateOfferingUseCase {

  OfferingResult update(UpdateOfferingCommand command);
}
