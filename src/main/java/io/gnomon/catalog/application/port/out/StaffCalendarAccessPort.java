package io.gnomon.catalog.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Resolves the single calendar assigned to a staff member inside a tenant. */
public interface StaffCalendarAccessPort {
  Optional<UUID> findCalendarIdForStaff(UUID tenantId, UUID userId);
}
