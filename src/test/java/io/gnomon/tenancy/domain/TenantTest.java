package io.gnomon.tenancy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.tenancy.domain.exception.TenancyException;
import io.gnomon.tenancy.domain.model.Tenant;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TenantTest {

  private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

  @Test
  void create_withValidValues_shouldApplyDefaults() {
    Tenant tenant =
        Tenant.create("Barbearia do João", "barbearia-do-joao", "America/Fortaleza", null, NOW);

    assertThat(tenant.currencyCode()).isEqualTo("BRL");
    assertThat(tenant.status()).isEqualTo(Tenant.TenantStatus.ACTIVE);
    assertThat(tenant.createdAt()).isEqualTo(NOW);
  }

  @Test
  void create_withInvalidTimezone_shouldReject() {
    assertThatThrownBy(() -> Tenant.create("Tenant", "tenant", "Mars/Olympus", "BRL", NOW))
        .isInstanceOf(TenancyException.class)
        .extracting(exception -> ((TenancyException) exception).code())
        .isEqualTo("invalid_timezone");
  }

  @Test
  void create_withNonCanonicalSlug_shouldReject() {
    assertThatThrownBy(
            () -> Tenant.create("Tenant", "Tenant With Spaces", "America/Fortaleza", "BRL", NOW))
        .isInstanceOf(TenancyException.class)
        .extracting(exception -> ((TenancyException) exception).code())
        .isEqualTo("validation_error");
  }
}
