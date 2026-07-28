package io.gnomon.booking.infrastructure.persistence;

import io.gnomon.booking.application.port.CustomerRepository;
import io.gnomon.booking.domain.BookingException;
import io.gnomon.booking.domain.Customer;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CustomerJdbcAdapter implements CustomerRepository {

  static final String INSERT_CUSTOMER =
      """
      INSERT INTO customers (name, phone, email)
      VALUES (?, ?, CAST(? AS citext))
      ON CONFLICT ON CONSTRAINT uq_customers_phone DO NOTHING
      """;

  static final String SELECT_BY_PHONE =
      """
      SELECT id, name, phone, email::text AS email
      FROM customers
      WHERE phone = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  CustomerJdbcAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Customer findOrCreate(String name, String canonicalPhone, String normalizedEmail) {
    try {
      jdbcTemplate.update(INSERT_CUSTOMER, name, canonicalPhone, normalizedEmail);
    } catch (DataIntegrityViolationException exception) {
      if (BookingConstraintNames.contains(exception, "ck_customers_name_not_blank")
          || BookingConstraintNames.contains(exception, "ck_customers_phone_not_blank")) {
        throw new BookingException(
            "validation_error", "customer data violates a database constraint");
      }
      throw exception;
    }
    return jdbcTemplate.queryForObject(
        SELECT_BY_PHONE, CustomerJdbcAdapter::mapCustomer, canonicalPhone);
  }

  private static Customer mapCustomer(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Customer(
        resultSet.getObject("id", java.util.UUID.class),
        resultSet.getString("name"),
        resultSet.getString("phone"),
        resultSet.getString("email"));
  }
}
