package io.gnomon.availability.infrastructure.booking;

import io.gnomon.availability.application.port.OccupiedSlotPort;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Phase 03 adapter. Appointments replace this implementation in phase 04. */
@Component
class EmptyOccupiedSlotAdapter implements OccupiedSlotPort {

  @Override
  public Set<Instant> findOccupied(
      UUID tenantId, UUID calendarId, Instant fromInclusive, Instant toExclusive) {
    return Set.of();
  }
}
