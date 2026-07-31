package io.gnomon.booking.infrastructure.persistence.adapter;

import io.gnomon.booking.application.port.out.SlotRetentionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SlotRetentionJdbcAdapter implements SlotRetentionRepository {

  static final String DELETE_EXPIRED_BATCH =
      """
      WITH expired AS (
          SELECT tenant_id, calendar_id, slot_start_at
          FROM appointment_slots
          WHERE slot_start_at < ?
          ORDER BY slot_start_at
          FOR UPDATE SKIP LOCKED
          LIMIT ?
      )
      DELETE FROM appointment_slots slots
      USING expired
      WHERE slots.tenant_id = expired.tenant_id
        AND slots.calendar_id = expired.calendar_id
        AND slots.slot_start_at = expired.slot_start_at
      """;

  private final JdbcTemplate jdbcTemplate;

  public SlotRetentionJdbcAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public int deleteExpiredBatch(Instant cutoff, int limit) {
    return jdbcTemplate.update(DELETE_EXPIRED_BATCH, Timestamp.from(cutoff), limit);
  }
}
