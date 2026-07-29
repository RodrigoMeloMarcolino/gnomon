package io.gnomon.catalog.domain.model;

import io.gnomon.catalog.domain.exception.CatalogException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Collaborator {

  private final UUID id;
  private final UUID tenantId;
  private UUID userId;
  private String displayName;
  private boolean active;
  private final Instant createdAt;
  private Instant updatedAt;

  public Collaborator(
      UUID id,
      UUID tenantId,
      UUID userId,
      String displayName,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.userId = userId;
    this.displayName = validName(displayName);
    this.active = active;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  public static Collaborator create(UUID tenantId, String displayName, Instant now) {
    return new Collaborator(UUID.randomUUID(), tenantId, null, displayName, true, now, now);
  }

  public void rename(String name, Instant now) {
    displayName = validName(name);
    updatedAt = now;
  }

  public void link(UUID localUserId, Instant now) {
    if (userId != null && !userId.equals(localUserId)) {
      throw new CatalogException("collaborator_already_linked", "collaborator is already linked");
    }
    userId = Objects.requireNonNull(localUserId);
    updatedAt = now;
  }

  public UUID unlink(Instant now) {
    UUID previous = userId;
    userId = null;
    updatedAt = now;
    return previous;
  }

  public void deactivate(Instant now) {
    active = false;
    updatedAt = now;
  }

  private static String validName(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120) {
      throw new CatalogException("validation_error", "display name is invalid");
    }
    return value.strip();
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID userId() {
    return userId;
  }

  public String displayName() {
    return displayName;
  }

  public boolean active() {
    return active;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
