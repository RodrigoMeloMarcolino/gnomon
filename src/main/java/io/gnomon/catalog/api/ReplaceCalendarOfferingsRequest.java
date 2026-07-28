package io.gnomon.catalog.api;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

public record ReplaceCalendarOfferingsRequest(@NotNull Set<@NotNull UUID> offeringIds) {}
