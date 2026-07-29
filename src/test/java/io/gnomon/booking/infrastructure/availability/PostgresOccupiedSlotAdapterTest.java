package io.gnomon.booking.infrastructure.integration.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.gnomon.availability.infrastructure.persistence.adapter.PostgresOccupiedSlotAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PostgresOccupiedSlotAdapterTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  @SuppressWarnings("unchecked")
  void findOccupied_whenDatabaseReturnsSlots_shouldReturnImmutableSet() {
    UUID tenantId = UUID.randomUUID();
    UUID calendarId = UUID.randomUUID();
    Instant from = Instant.parse("2027-07-01T00:00:00Z");
    Instant to = Instant.parse("2027-07-02T00:00:00Z");
    Instant occupied = Instant.parse("2027-07-01T12:00:00Z");
    when(jdbcTemplate.query(
            any(String.class),
            any(RowMapper.class),
            eq(tenantId),
            eq(calendarId),
            eq(Timestamp.from(from)),
            eq(Timestamp.from(to))))
        .thenReturn(List.of(occupied, occupied));

    var result =
        new PostgresOccupiedSlotAdapter(jdbcTemplate).findOccupied(tenantId, calendarId, from, to);

    assertThat(result).containsExactly(occupied);
  }
}
