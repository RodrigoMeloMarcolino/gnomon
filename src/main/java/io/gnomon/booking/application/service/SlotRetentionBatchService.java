package io.gnomon.booking.application.service;

import io.gnomon.booking.application.port.out.SlotRetentionRepository;
import io.gnomon.shared.domain.model.SlotRetentionPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotRetentionBatchService {

  private final SlotRetentionRepository slots;
  private final SlotRetentionPolicy policy;
  private final Clock clock;

  public SlotRetentionBatchService(
      SlotRetentionRepository slots, SlotRetentionPolicy policy, Clock clock) {
    this.slots = slots;
    this.policy = policy;
    this.clock = clock;
  }

  @Transactional
  public int deleteExpiredBatch() {
    Instant cutoff = clock.instant().minus(policy.retention());
    return slots.deleteExpiredBatch(cutoff, policy.batchSize());
  }
}
