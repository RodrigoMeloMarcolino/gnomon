package io.gnomon.booking.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface StaffCalendarAccessPort {
  Optional<UUID> findCalendarIdForStaff(UUID tenantId, UUID userId);
}
