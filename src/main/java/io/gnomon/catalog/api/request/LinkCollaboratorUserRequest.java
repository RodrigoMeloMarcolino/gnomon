package io.gnomon.catalog.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LinkCollaboratorUserRequest(@NotBlank @Email String email) {}
