package io.gnomon.shared.infrastructure.config;

import io.gnomon.shared.domain.model.SlotRetentionPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gnomon.booking.slot-retention")
public record SlotRetentionProperties(
    Duration retention, Duration cleanupInterval, int batchSize, int maxBatchesPerRun) {

  public SlotRetentionProperties {
    if (retention == null) {
      retention = Duration.ofDays(30);
    }
    if (cleanupInterval == null) {
      cleanupInterval = Duration.ofHours(1);
    }
    if (batchSize == 0) {
      batchSize = 10_000;
    }
    if (maxBatchesPerRun == 0) {
      maxBatchesPerRun = 100;
    }
  }

  public SlotRetentionPolicy toPolicy() {
    return new SlotRetentionPolicy(retention, batchSize, maxBatchesPerRun);
  }
}
