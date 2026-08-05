package io.gnomon.booking.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record AdminAppointment(
    UUID id,
    UUID tenantId,
    UUID calendarId,
    Instant startAt,
    Instant endAt,
    String status,
    CalendarSummary calendar,
    OfferingSummary offering,
    CustomerSummary customer,
    String customerNotes) {}
