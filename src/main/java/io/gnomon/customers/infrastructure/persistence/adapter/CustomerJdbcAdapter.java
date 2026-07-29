package io.gnomon.customers.infrastructure.persistence.adapter;

import io.gnomon.customers.application.port.out.CustomerRepository;
import io.gnomon.customers.domain.exception.CustomerException;
import io.gnomon.customers.domain.model.Customer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerJdbcAdapter implements CustomerRepository {

  public static final String INSERT_CUSTOMER =
      """
      INSERT INTO customers (name, phone, email)
      VALUES (?, ?, CAST(? AS citext))
      ON CONFLICT ON CONSTRAINT uq_customers_phone DO NOTHING
      """;

  public static final String SELECT_BY_PHONE =
      """
      SELECT id, name, phone, email::text AS email
      FROM customers
      WHERE phone = ?
      """;

  private final JdbcTemplate jdbcTemplate;

  public CustomerJdbcAdapter(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Customer findOrCreate(String name, String canonicalPhone, String normalizedEmail) {
    try {
      jdbcTemplate.update(INSERT_CUSTOMER, name, canonicalPhone, normalizedEmail);
    } catch (DataIntegrityViolationException exception) {
      if (constraintContains(exception, "ck_customers_name_not_blank")
          || constraintContains(exception, "ck_customers_phone_not_blank")) {
        throw new CustomerException(
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

  private static boolean constraintContains(RuntimeException exception, String name) {
    Throwable current = exception;
    while (current != null) {
      if (current.getMessage() != null
          && current
              .getMessage()
              .toLowerCase(Locale.ROOT)
              .contains(name.toLowerCase(Locale.ROOT))) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
