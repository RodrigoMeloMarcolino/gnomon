package io.gnomon.catalog.api.request;

import io.gnomon.catalog.api.validation.PositiveMultipleOf15;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateOfferingRequest(
    @NotBlank @Size(max = 120) String title,
    String description,
    @NotNull @PositiveMultipleOf15 Integer durationMinutes,
    @PositiveOrZero Integer priceCents) {}
