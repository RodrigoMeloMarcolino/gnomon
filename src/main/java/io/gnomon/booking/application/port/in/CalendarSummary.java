package io.gnomon.booking.application.port.in;

import java.util.UUID;

public record CalendarSummary(UUID id, String name, String timezone) {}
