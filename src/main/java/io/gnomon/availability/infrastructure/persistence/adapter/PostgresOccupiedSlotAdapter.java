package io.gnomon.availability.infrastructure.persistence.adapter;

import io.gnomon.availability.application.port.out.OccupiedSlotPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresOccupiedSlotAdapter implements OccupiedSlotPort {

  private static final String SELECT_OCCUPIED =
      """
      SELECT slot_start_at
      FROM appointment_slots
      WHERE tenant_id = ?
        AND calendar_id = ?
        AND slot_start_at >= ?
        AND slot_start_at < ?
      ORDER BY slot_start_at
      """;

  private final JdbcTemplate jdbcTemplate;

  public PostgresOccupiedSlotAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Set<Instant> findOccupied(
      UUID tenantId, UUID calendarId, Instant fromInclusive, Instant toExclusive) {
    return jdbcTemplate
        .query(
            SELECT_OCCUPIED,
            (resultSet, rowNumber) -> resultSet.getTimestamp("slot_start_at").toInstant(),
            tenantId,
            calendarId,
            Timestamp.from(fromInclusive),
            Timestamp.from(toExclusive))
        .stream()
        .collect(Collectors.toUnmodifiableSet());
  }
}
