package io.gnomon.booking.infrastructure.scheduler;

import io.gnomon.booking.application.service.SlotRetentionBatchService;
import io.gnomon.shared.infrastructure.config.SlotRetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "gnomon.booking.slot-retention.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SlotRetentionScheduler {

  private static final Logger LOGGER = LoggerFactory.getLogger(SlotRetentionScheduler.class);

  private final SlotRetentionBatchService batches;
  private final SlotRetentionProperties properties;

  public SlotRetentionScheduler(
      SlotRetentionBatchService batches, SlotRetentionProperties properties) {
    this.batches = batches;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${gnomon.booking.slot-retention.cleanup-interval:1h}")
  public void deleteExpiredSlots() {
    int total = 0;
    for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
      int deleted = batches.deleteExpiredBatch();
      total += deleted;
      if (deleted < properties.batchSize()) {
        break;
      }
    }
    if (total > 0) {
      LOGGER.info("event_name=appointment.slot_retention_deleted slots={}", total);
    }
  }
}
