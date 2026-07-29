package io.gnomon.availability.application.port.out;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface OccupiedSlotPort {

  Set<Instant> findOccupied(
      UUID tenantId, UUID calendarId, Instant fromInclusive, Instant toExclusive);
}
