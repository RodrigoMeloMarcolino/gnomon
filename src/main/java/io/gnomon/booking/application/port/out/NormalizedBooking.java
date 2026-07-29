package io.gnomon.booking.application.port.out;

public record NormalizedBooking(
    String calendarId,
    String offeringId,
    String startAt,
    String customerName,
    String customerPhone,
    String customerEmail,
    String customerNotes) {}
