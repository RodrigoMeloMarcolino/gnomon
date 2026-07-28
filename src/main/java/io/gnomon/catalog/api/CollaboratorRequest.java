package io.gnomon.catalog.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollaboratorRequest(@NotBlank @Size(max = 120) String displayName) {}
