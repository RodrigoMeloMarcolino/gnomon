package io.gnomon.customers.application.port.in;

import java.util.UUID;

public record CustomerResult(UUID id, String name, String phone, String email) {}
