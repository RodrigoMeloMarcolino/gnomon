package io.gnomon.availability.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalTime;

public record UpdateAvailabilityRuleRequest(
    @Min(1) @Max(7) Integer weekday, LocalTime startTime, LocalTime endTime, Boolean active) {}
