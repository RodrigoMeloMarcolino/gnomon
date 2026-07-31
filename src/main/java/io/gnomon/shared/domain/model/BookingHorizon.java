package io.gnomon.shared.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Defines the bounded window in which public bookings may be requested. */
public record BookingHorizon(int maxAdvanceDays) {

  public static final int DEFAULT_MAX_ADVANCE_DAYS = 180;

  public BookingHorizon {
    if (maxAdvanceDays <= 0) {
      throw new IllegalArgumentException("maxAdvanceDays must be positive");
    }
  }

  public boolean allows(Instant startAt, Instant now) {
    return !startAt.isAfter(latestStart(now));
  }

  public boolean allows(LocalDate date, ZoneId zoneId, Instant now) {
    return !date.atStartOfDay(zoneId).toInstant().isAfter(latestStart(now));
  }

  private Instant latestStart(Instant now) {
    return now.plusSeconds(maxAdvanceDays * 24L * 60L * 60L);
  }
}
