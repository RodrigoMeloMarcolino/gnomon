package io.gnomon.catalog.application.port.out;

import java.util.UUID;

public record UserLink(UUID userId, String email, String displayName, String membershipRole) {}
