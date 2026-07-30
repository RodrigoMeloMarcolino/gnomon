package io.gnomon.catalog.application.port.out;

import io.gnomon.catalog.application.port.in.PublicTenantProfileResult;
import io.gnomon.catalog.application.port.in.result.OfferingResult;
import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Cache-aside policy offered to catalog public-read use cases. */
public interface PublicCatalogCachePort {

  PublicTenantProfileResult profile(
      UUID tenantId, Supplier<PublicTenantProfileResult> databaseLookup);

  List<PublicCalendarResult> calendars(
      UUID tenantId, Supplier<List<PublicCalendarResult>> databaseLookup);

  List<OfferingResult> offerings(
      UUID tenantId, UUID calendarId, Supplier<List<OfferingResult>> databaseLookup);

  void invalidateAfterCommit(UUID tenantId);
}
