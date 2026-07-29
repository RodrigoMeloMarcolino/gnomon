package io.gnomon.booking.domain.service;

import java.time.Instant;
import java.util.List;

public interface SlotGenerator {

  List<Instant> generate(Instant startAt, int durationMinutes);
}
