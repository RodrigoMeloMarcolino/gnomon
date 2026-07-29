package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;

public interface CreateOfferingUseCase {

  OfferingResult create(CreateOfferingCommand command);
}
