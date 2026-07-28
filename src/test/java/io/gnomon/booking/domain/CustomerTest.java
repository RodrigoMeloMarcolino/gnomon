package io.gnomon.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

  @Test
  void constructor_withValidValues_shouldNormalizeProfileText() {
    Customer customer =
        new Customer(
            UUID.randomUUID(), "  Alice Smith  ", "+5585999999999", " ALICE@EXAMPLE.COM ");

    assertThat(customer.name()).isEqualTo("Alice Smith");
    assertThat(customer.phone()).isEqualTo("+5585999999999");
    assertThat(customer.email()).isEqualTo("alice@example.com");
  }

  @Test
  void constructor_withBlankEmail_shouldNormalizeToNull() {
    Customer customer = new Customer(UUID.randomUUID(), "Alice", "+5585999999999", " ");

    assertThat(customer.email()).isNull();
  }

  @Test
  void constructor_withBlankName_shouldReject() {
    assertThatThrownBy(() -> new Customer(UUID.randomUUID(), " ", "+5585999999999", null))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("validation_error");
  }

  @Test
  void constructor_withOversizedName_shouldReject() {
    assertThatThrownBy(
            () -> new Customer(UUID.randomUUID(), "a".repeat(121), "+5585999999999", null))
        .isInstanceOf(BookingException.class)
        .hasMessageContaining("at most 120");
  }

  @Test
  void constructor_withNonCanonicalPhone_shouldReject() {
    assertThatThrownBy(() -> new Customer(UUID.randomUUID(), "Alice", "(85) 99999-9999", null))
        .isInstanceOf(BookingException.class)
        .extracting(exception -> ((BookingException) exception).code())
        .isEqualTo("phone_invalid");
  }
}
