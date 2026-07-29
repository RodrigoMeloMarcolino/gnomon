package io.gnomon.booking.application.port.in;

public record CreationResult(AppointmentResult appointment, boolean replayed) {}
