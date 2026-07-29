package io.gnomon.booking.application.port.in;

import java.util.UUID;

public record OfferingSummary(UUID id, String title, int durationMinutes, Integer priceCents) {}
