package io.gnomon.booking.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.booking.application.port.out.SlotRetentionRepository;
import io.gnomon.booking.application.service.SlotRetentionBatchService;
import io.gnomon.shared.domain.model.SlotRetentionPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotRetentionBatchServiceTest {

  @Mock private SlotRetentionRepository repository;

  @Test
  void deletesOneBoundedBatchBeforeRetentionCutoff() {
    Instant now = Instant.parse("2027-07-31T12:00:00Z");
    var service =
        new SlotRetentionBatchService(
            repository,
            new SlotRetentionPolicy(Duration.ofDays(30), 10_000, 100),
            Clock.fixed(now, ZoneOffset.UTC));
    when(repository.deleteExpiredBatch(now.minus(Duration.ofDays(30)), 10_000)).thenReturn(42);

    org.assertj.core.api.Assertions.assertThat(service.deleteExpiredBatch()).isEqualTo(42);
    verify(repository).deleteExpiredBatch(now.minus(Duration.ofDays(30)), 10_000);
  }
}
