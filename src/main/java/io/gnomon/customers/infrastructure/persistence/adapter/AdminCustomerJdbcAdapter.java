package io.gnomon.customers.infrastructure.persistence.adapter;

import io.gnomon.customers.application.port.in.CustomerPage;
import io.gnomon.customers.application.port.in.CustomerResult;
import io.gnomon.customers.application.port.out.AdminCustomerQueryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class AdminCustomerJdbcAdapter implements AdminCustomerQueryPort {
  private static final String BASE =
      "SELECT DISTINCT c.id, c.name, c.phone, c.email::text email FROM customers c JOIN appointments a ON a.customer_id=c.id WHERE a.tenant_id=?";
  private final JdbcTemplate jdbc;

  AdminCustomerJdbcAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public CustomerPage findPage(UUID tenantId, int page, int size) {
    long total =
        jdbc.queryForObject(
            "SELECT count(DISTINCT customer_id) FROM appointments WHERE tenant_id=?",
            Long.class,
            tenantId);
    List<CustomerResult> content =
        jdbc.query(
            BASE + " ORDER BY c.name ASC, c.id ASC LIMIT ? OFFSET ?",
            AdminCustomerJdbcAdapter::map,
            tenantId,
            size,
            page * size);
    int pages = total == 0 ? 0 : (int) ((total + size - 1) / size);
    return new CustomerPage(content, page, size, total, pages, page + 1 >= pages);
  }

  @Override
  public Optional<CustomerResult> findByTenantIdAndId(UUID tenantId, UUID id) {
    return jdbc.query(BASE + " AND c.id=?", AdminCustomerJdbcAdapter::map, tenantId, id).stream()
        .findFirst();
  }

  @Override
  public boolean existsById(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM customers WHERE id=?)", Boolean.class, id));
  }

  private static CustomerResult map(ResultSet rs, int row) throws SQLException {
    return new CustomerResult(
        rs.getObject("id", UUID.class),
        rs.getString("name"),
        rs.getString("phone"),
        rs.getString("email"));
  }
}
