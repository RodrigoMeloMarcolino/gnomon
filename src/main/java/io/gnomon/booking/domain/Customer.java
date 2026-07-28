package io.gnomon.booking.domain;

import java.util.UUID;

public record Customer(UUID id, String name, String phone, String email) {}
