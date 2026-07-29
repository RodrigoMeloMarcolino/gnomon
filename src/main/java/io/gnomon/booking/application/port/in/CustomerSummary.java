package io.gnomon.booking.application.port.in;

import java.util.UUID;

public record CustomerSummary(UUID id, String name, String phone, String email) {}
