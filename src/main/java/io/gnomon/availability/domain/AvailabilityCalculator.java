package io.gnomon.availability.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public interface AvailabilityCalculator {

  List<Instant> availableStarts(
      List<AvailabilityWindow> rules,
      int durationMinutes,
      Set<Instant> occupied,
      LocalDate date,
      ZoneId zone,
      Instant now);
}
