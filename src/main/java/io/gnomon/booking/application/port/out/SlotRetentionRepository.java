package io.gnomon.booking.application.port.out;

import java.time.Instant;

public interface SlotRetentionRepository {

  int deleteExpiredBatch(Instant cutoff, int limit);
}
