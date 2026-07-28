package io.gnomon.availability.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public record AvailabilityWindow(
    DayOfWeek weekday, LocalTime startTime, LocalTime endTime, boolean active) {

  public AvailabilityWindow {
    Objects.requireNonNull(weekday, "weekday is required");
    validateTime(startTime, "start_time");
    validateTime(endTime, "end_time");
    if (!startTime.isBefore(endTime)) {
      throw new AvailabilityException("validation_error", "start_time must be before end_time");
    }
  }

  private static void validateTime(LocalTime value, String field) {
    if (value == null
        || value.getMinute() % 15 != 0
        || value.getSecond() != 0
        || value.getNano() != 0) {
      throw new AvailabilityException("validation_error", field + " must be aligned to 15 minutes");
    }
  }
}
