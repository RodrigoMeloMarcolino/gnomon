package io.gnomon.catalog.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCollaboratorRequest(@NotBlank @Size(max = 120) String displayName) {}
