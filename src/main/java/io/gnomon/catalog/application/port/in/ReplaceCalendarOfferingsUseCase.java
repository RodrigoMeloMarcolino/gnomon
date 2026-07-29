package io.gnomon.catalog.application.port.in;

import io.gnomon.catalog.application.port.in.result.OfferingResult;
import java.util.List;

public interface ReplaceCalendarOfferingsUseCase {

  List<OfferingResult> replace(ReplaceCalendarOfferingsCommand command);
}
