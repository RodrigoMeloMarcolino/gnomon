package io.gnomon.booking.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.customers.domain.exception.CustomerException;
import io.gnomon.customers.domain.model.Customer;
import io.gnomon.customers.infrastructure.persistence.adapter.CustomerJdbcAdapter;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class CustomerJdbcAdapterTest {

  @Mock private JdbcTemplate jdbcTemplate;

  @Test
  void findOrCreate_whenPhoneAlreadyExists_shouldReturnPersistedCustomer() {
    var persisted = new Customer(UUID.randomUUID(), "Original", "+5585999999999", "old@test.dev");
    when(jdbcTemplate.queryForObject(
            eq(CustomerJdbcAdapter.SELECT_BY_PHONE), any(RowMapper.class), eq("+5585999999999")))
        .thenReturn(persisted);
    var adapter = new CustomerJdbcAdapter(jdbcTemplate);

    Customer result = adapter.findOrCreate("Submitted", "+5585999999999", "new-email@test.dev");

    assertThat(result).isSameAs(persisted);
    verify(jdbcTemplate)
        .update(
            CustomerJdbcAdapter.INSERT_CUSTOMER,
            "Submitted",
            "+5585999999999",
            "new-email@test.dev");
  }

  @Test
  void findOrCreate_whenKnownCheckFails_shouldTranslateValidationError() {
    var failure =
        new DataIntegrityViolationException(
            "insert failed", new SQLException("constraint CK_CUSTOMERS_NAME_NOT_BLANK"));
    when(jdbcTemplate.update(CustomerJdbcAdapter.INSERT_CUSTOMER, "", "+5585999999999", null))
        .thenThrow(failure);
    var adapter = new CustomerJdbcAdapter(jdbcTemplate);

    assertThatThrownBy(() -> adapter.findOrCreate("", "+5585999999999", null))
        .isInstanceOfSatisfying(
            CustomerException.class,
            exception -> assertThat(exception.code()).isEqualTo("validation_error"));
  }
}
