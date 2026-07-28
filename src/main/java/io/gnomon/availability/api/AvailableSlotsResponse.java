package io.gnomon.availability.api;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record AvailableSlotsResponse(List<Instant> availableStartTimes) {}
