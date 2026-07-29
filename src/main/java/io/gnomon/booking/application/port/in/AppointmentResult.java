package io.gnomon.booking.application.port.in;

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
    String customerNotes) {}
