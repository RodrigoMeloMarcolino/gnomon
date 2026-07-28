package io.gnomon.availability.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record CreateAvailabilityRuleRequest(
    @Min(1) @Max(7) int weekday, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
