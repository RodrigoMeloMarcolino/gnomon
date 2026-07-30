package io.gnomon.availability.application.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Cache-aside policy for public availability reads, owned by the availability module. */
public interface PublicAvailabilityCachePort {

  List<Instant> availableSlots(
      UUID tenantId,
      UUID calendarId,
      UUID offeringId,
      LocalDate calendarLocalDate,
      Supplier<List<Instant>> databaseLookup);

  /**
   * Invalidates every cached local day for a calendar only after the originating mutation commits.
   */
  void invalidateCalendarAfterCommit(UUID tenantId, UUID calendarId);

  /** Invalidates the exact cached offering/day entry after a successful booking commits. */
  void invalidateDayAfterCommit(
      UUID tenantId, UUID calendarId, UUID offeringId, LocalDate calendarLocalDate);
}
