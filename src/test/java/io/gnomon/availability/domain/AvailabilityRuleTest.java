package io.gnomon.availability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.availability.domain.model.AvailabilityRule;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityRuleTest {

  private static final Instant NOW = Instant.parse("2027-07-01T12:00:00Z");

  @Test
  void create_whenWindowIsAligned_shouldCreateActiveRule() {
    AvailabilityRule rule =
        AvailabilityRule.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DayOfWeek.MONDAY,
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            NOW);

    assertThat(rule.active()).isTrue();
    assertThat(rule.toWindow().weekday()).isEqualTo(DayOfWeek.MONDAY);
  }

  @Test
  void create_whenTimeIsNotAligned_shouldRejectRule() {
    assertThatThrownBy(
            () ->
                AvailabilityRule.create(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    DayOfWeek.MONDAY,
                    LocalTime.of(9, 7),
                    LocalTime.of(17, 0),
                    NOW))
        .isInstanceOf(AvailabilityException.class)
        .hasMessageContaining("15 minutes");
  }

  @Test
  void update_whenWindowIsInvalid_shouldPreserveCurrentState() {
    AvailabilityRule rule =
        AvailabilityRule.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            DayOfWeek.MONDAY,
            LocalTime.of(9, 0),
            LocalTime.of(17, 0),
            NOW);

    assertThatThrownBy(
            () -> rule.update(null, LocalTime.of(18, 0), null, null, NOW.plusSeconds(60)))
        .isInstanceOf(AvailabilityException.class);
    assertThat(rule.startTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(rule.endTime()).isEqualTo(LocalTime.of(17, 0));
  }
}
