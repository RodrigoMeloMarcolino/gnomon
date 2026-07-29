package io.gnomon.booking.domain.service;

import io.gnomon.booking.domain.exception.BookingDomainException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultSlotGenerator implements SlotGenerator {

  private static final int SLOT_MINUTES = 15;
  private static final long SLOT_SECONDS = SLOT_MINUTES * 60L;

  @Override
  public List<Instant> generate(Instant startAt, int durationMinutes) {
    Objects.requireNonNull(startAt, "startAt is required");
    validateDuration(durationMinutes);
    validateStart(startAt);

    int slotCount = durationMinutes / SLOT_MINUTES;
    var slots = new ArrayList<Instant>(slotCount);
    try {
      for (int index = 0; index < slotCount; index++) {
        slots.add(startAt.plusSeconds(Math.multiplyExact((long) index, SLOT_SECONDS)));
      }
    } catch (ArithmeticException | DateTimeException exception) {
      throw new BookingDomainException("validation_error", "slot interval is out of range");
    }
    return List.copyOf(slots);
  }

  private static void validateDuration(int durationMinutes) {
    if (durationMinutes <= 0 || durationMinutes % SLOT_MINUTES != 0) {
      throw new BookingDomainException(
          "validation_error", "duration_minutes must be a positive multiple of 15");
    }
  }

  private static void validateStart(Instant startAt) {
    if (startAt.getNano() != 0 || Math.floorMod(startAt.getEpochSecond(), SLOT_SECONDS) != 0) {
      throw new BookingDomainException(
          "validation_error", "start_at must be aligned to a 15-minute boundary");
    }
  }
}
