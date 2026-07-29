package io.gnomon.availability.domain.service;

import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.availability.domain.model.AvailabilityWindow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class DefaultAvailabilityCalculator implements AvailabilityCalculator {

  private static final int SLOT_MINUTES = 15;

  @Override
  public List<Instant> availableStarts(
      List<AvailabilityWindow> rules,
      int durationMinutes,
      Set<Instant> occupied,
      LocalDate date,
      ZoneId zone,
      Instant now) {
    Objects.requireNonNull(rules, "rules are required");
    Objects.requireNonNull(occupied, "occupied slots are required");
    Objects.requireNonNull(date, "date is required");
    Objects.requireNonNull(zone, "zone is required");
    Objects.requireNonNull(now, "now is required");
    validateDuration(durationMinutes);

    var available = new TreeSet<Instant>();
    for (AvailabilityWindow rule : rules) {
      Objects.requireNonNull(rule, "availability rule is required");
      if (!rule.active() || rule.weekday() != date.getDayOfWeek()) {
        continue;
      }
      addAvailableStarts(rule, durationMinutes, occupied, date, zone, now, available);
    }
    return List.copyOf(available);
  }

  private static void addAvailableStarts(
      AvailabilityWindow rule,
      int durationMinutes,
      Set<Instant> occupied,
      LocalDate date,
      ZoneId zone,
      Instant now,
      Set<Instant> available) {
    for (LocalTime start = rule.startTime();
        fitsInWindow(start, rule.endTime(), durationMinutes);
        start = start.plusMinutes(SLOT_MINUTES)) {
      LocalDateTime localStart = date.atTime(start);
      for (ZoneOffset offset : zone.getRules().getValidOffsets(localStart)) {
        Instant candidate = localStart.toInstant(offset);
        if (candidate.isAfter(now)
            && doesNotIntersectOccupied(candidate, durationMinutes, occupied)) {
          available.add(candidate);
        }
      }
    }
  }

  private static boolean fitsInWindow(
      LocalTime candidate, LocalTime windowEnd, int durationMinutes) {
    return Duration.between(candidate, windowEnd).toMinutes() >= durationMinutes;
  }

  private static boolean doesNotIntersectOccupied(
      Instant candidate, int durationMinutes, Set<Instant> occupied) {
    int slotCount = durationMinutes / SLOT_MINUTES;
    for (int index = 0; index < slotCount; index++) {
      if (occupied.contains(candidate.plus(Duration.ofMinutes((long) index * SLOT_MINUTES)))) {
        return false;
      }
    }
    return true;
  }

  private static void validateDuration(int durationMinutes) {
    if (durationMinutes <= 0 || durationMinutes % SLOT_MINUTES != 0) {
      throw new AvailabilityException(
          "validation_error", "duration_minutes must be a positive multiple of 15");
    }
  }
}
