package io.gnomon.booking.domain;

import java.time.Instant;
import java.util.List;

public interface SlotGenerator {

  List<Instant> generate(Instant startAt, int durationMinutes);
}
