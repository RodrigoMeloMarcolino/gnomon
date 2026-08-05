package io.gnomon.booking.infrastructure.persistence.adapter;

import io.gnomon.booking.application.port.in.AdminAppointment;
import io.gnomon.booking.application.port.in.AdminAppointmentPage;
import io.gnomon.booking.application.port.in.CalendarSummary;
import io.gnomon.booking.application.port.in.CustomerSummary;
import io.gnomon.booking.application.port.in.OfferingSummary;
import io.gnomon.booking.application.port.out.AdminAppointmentQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminAppointmentJdbcAdapter implements AdminAppointmentQueryPort {
  private static final String SELECT =
      """
      SELECT a.id, a.tenant_id, a.calendar_id, a.start_at, a.end_at, a.status, a.customer_notes,
             c.name calendar_name, c.timezone calendar_timezone,
             o.id offering_id, o.title offering_title, o.duration_minutes, o.price_cents,
             u.id customer_id, u.name customer_name, u.phone customer_phone, u.email::text customer_email
      FROM appointments a JOIN calendars c ON c.tenant_id=a.tenant_id AND c.id=a.calendar_id
      JOIN offerings o ON o.tenant_id=a.tenant_id AND o.id=a.offering_id
      JOIN customers u ON u.id=a.customer_id
      WHERE a.tenant_id = ? AND a.start_at >= ? AND a.start_at < ?
      """;
  private final JdbcTemplate jdbc;

  AdminAppointmentJdbcAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public AdminAppointmentPage findPage(
      UUID tenantId,
      java.time.Instant from,
      java.time.Instant to,
      UUID calendarId,
      String status,
      int page,
      int size) {
    String filters =
        (calendarId == null ? "" : " AND a.calendar_id = ?")
            + (status == null ? "" : " AND a.status = ?");
    List<Object> args =
        new ArrayList<>(List.of(tenantId, Timestamp.from(from), Timestamp.from(to)));
    if (calendarId != null) args.add(calendarId);
    if (status != null) args.add(status);
    long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM appointments a WHERE a.tenant_id = ? AND a.start_at >= ? AND a.start_at < ?"
                + filters,
            Long.class,
            args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add(page * size);
    List<AdminAppointment> content =
        jdbc.query(
            SELECT + filters + " ORDER BY a.start_at ASC, a.id ASC LIMIT ? OFFSET ?",
            AdminAppointmentJdbcAdapter::map,
            pageArgs.toArray());
    int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return new AdminAppointmentPage(content, page, size, total, pages, page + 1 >= pages);
  }

  @Override
  public Optional<AdminAppointment> findByTenantIdAndId(UUID tenantId, UUID id) {
    return jdbc
        .query(
            SELECT.replace(" AND a.start_at >= ? AND a.start_at < ?", " AND a.id = ?"),
            AdminAppointmentJdbcAdapter::map,
            tenantId,
            id)
        .stream()
        .findFirst();
  }

  private static AdminAppointment map(ResultSet rs, int row) throws SQLException {
    return new AdminAppointment(
        rs.getObject("id", UUID.class),
        rs.getObject("tenant_id", UUID.class),
        rs.getObject("calendar_id", UUID.class),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getString("status"),
        new CalendarSummary(
            rs.getObject("calendar_id", UUID.class),
            rs.getString("calendar_name"),
            rs.getString("calendar_timezone")),
        new OfferingSummary(
            rs.getObject("offering_id", UUID.class),
            rs.getString("offering_title"),
            rs.getInt("duration_minutes"),
            rs.getObject("price_cents", Integer.class)),
        new CustomerSummary(
            rs.getObject("customer_id", UUID.class),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("customer_email")),
        rs.getString("customer_notes"));
  }
}
