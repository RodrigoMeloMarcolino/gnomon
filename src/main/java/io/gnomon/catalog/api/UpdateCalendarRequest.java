package io.gnomon.catalog.api;

import jakarta.validation.constraints.Size;

public record UpdateCalendarRequest(
    @Size(min = 1, max = 120) String name,
    @Size(min = 1, max = 64) String timezone,
    Boolean active) {}
