package io.gnomon.booking.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record CreateAppointmentRequest(
    @NotNull UUID calendarId,
    @NotNull UUID offeringId,
    @NotNull OffsetDateTime startAt,
    @NotBlank @Size(max = 120) String customerName,
    @NotBlank @Size(max = 64) String customerPhone,
    @Email @Size(max = 254) String customerEmail,
    String customerNotes) {}
