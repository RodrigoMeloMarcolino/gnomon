package io.gnomon.catalog.application;

import java.util.List;
import java.util.UUID;

public interface ListPublicOfferingsUseCase {

  List<OfferingResult> list(String tenantSlug, UUID calendarId);
}
