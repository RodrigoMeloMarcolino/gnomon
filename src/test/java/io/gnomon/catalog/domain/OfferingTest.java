package io.gnomon.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Offering;
import io.gnomon.catalog.domain.model.Offering.Change;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfferingTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Test
  void create_whenValuesAreValid_shouldNormalizeAndActivateOffering() {
    Offering offering =
        Offering.create(TENANT_ID, "  Corte Solar  ", "  Completo  ", 30, 4_500, NOW);

    assertThat(offering.title()).isEqualTo("Corte Solar");
    assertThat(offering.description()).isEqualTo("Completo");
    assertThat(offering.durationMinutes()).isEqualTo(30);
    assertThat(offering.priceCents()).isEqualTo(4_500);
    assertThat(offering.active()).isTrue();
  }

  @Test
  void create_whenDurationIsNotPositiveMultipleOf15_shouldReject() {
    assertThatThrownBy(() -> Offering.create(TENANT_ID, "Corte", null, 20, null, NOW))
        .isInstanceOf(CatalogException.class)
        .extracting(error -> ((CatalogException) error).code())
        .isEqualTo("validation_error");
  }

  @Test
  void create_whenPriceIsNegative_shouldReject() {
    assertThatThrownBy(() -> Offering.create(TENANT_ID, "Corte", null, 30, -1, NOW))
        .isInstanceOf(CatalogException.class)
        .hasMessageContaining("price");
  }

  @Test
  void update_whenNullableFieldsAreExplicitlyCleared_shouldPreserveOtherValues() {
    Offering offering = Offering.create(TENANT_ID, "Corte", "Descrição", 30, 4_500, NOW);
    Instant later = NOW.plusSeconds(60);

    offering.update(
        Change.unchanged(),
        Change.to(null),
        Change.unchanged(),
        Change.to(null),
        Change.unchanged(),
        later);

    assertThat(offering.title()).isEqualTo("Corte");
    assertThat(offering.description()).isNull();
    assertThat(offering.durationMinutes()).isEqualTo(30);
    assertThat(offering.priceCents()).isNull();
    assertThat(offering.updatedAt()).isEqualTo(later);
  }

  @Test
  void deactivate_shouldKeepHistoricalOfferingAndChangeActiveFlag() {
    Offering offering = Offering.create(TENANT_ID, "Corte", null, 30, null, NOW);

    offering.deactivate(NOW.plusSeconds(60));

    assertThat(offering.active()).isFalse();
  }
}
