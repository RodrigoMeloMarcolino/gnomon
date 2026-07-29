package io.gnomon.tenancy.application.port.in;

public record ProvisionLocalUserCommand(String keycloakSubject, String email, String displayName) {}
