package io.gnomon.catalog.application.port.in;

import java.util.Optional;
import java.util.UUID;

/** Cross-module input port for resolving a staff member's own calendar. */
public interface StaffCalendarAccessUseCase {
  Optional<UUID> findCalendarIdForStaff(UUID tenantId, UUID userId);
}
