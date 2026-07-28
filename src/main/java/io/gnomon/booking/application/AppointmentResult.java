package io.gnomon.booking.application;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResult(
    UUID id,
    Instant startAt,
    Instant endAt,
    String status,
    CalendarSummary calendar,
    OfferingSummary offering,
    CustomerSummary customer,
    String customerNotes) {

  public record CalendarSummary(UUID id, String name, String timezone) {}

  public record OfferingSummary(UUID id, String title, int durationMinutes, Integer priceCents) {}

  public record CustomerSummary(UUID id, String name, String phone, String email) {}
}
