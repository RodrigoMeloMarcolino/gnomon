package io.gnomon.shared.domain.model;

import java.time.Duration;

/** Operational policy for removing expired concurrency-lock rows. */
public record SlotRetentionPolicy(Duration retention, int batchSize, int maxBatchesPerRun) {

  public SlotRetentionPolicy {
    if (retention == null || retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("retention must be positive");
    }
    if (batchSize <= 0 || maxBatchesPerRun <= 0) {
      throw new IllegalArgumentException("batch limits must be positive");
    }
  }
}
