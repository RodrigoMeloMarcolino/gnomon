package io.gnomon.catalog.application;

import java.util.UUID;

public record PublicCalendarResult(
    UUID id, UUID collaboratorId, String collaboratorName, String name, String timezone) {}
