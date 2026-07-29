package io.gnomon.catalog.domain.model;

import io.gnomon.catalog.domain.exception.CatalogException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Objects;
import java.util.UUID;

public final class Calendar {

  private final UUID id;
  private final UUID tenantId;
  private final UUID collaboratorId;
  private String name;
  private String timezone;
  private boolean active;
  private final Instant createdAt;
  private Instant updatedAt;

  public Calendar(
      UUID id,
      UUID tenantId,
      UUID collaboratorId,
      String name,
      String timezone,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.collaboratorId = Objects.requireNonNull(collaboratorId);
    this.name = validName(name);
    this.timezone = validTimezone(timezone);
    this.active = active;
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  public static Calendar create(
      UUID tenantId, UUID collaboratorId, String name, String timezone, Instant now) {
    return new Calendar(
        UUID.randomUUID(), tenantId, collaboratorId, name, timezone, true, now, now);
  }

  public void update(String newName, String newTimezone, Boolean newActive, Instant now) {
    if (newName != null) {
      name = validName(newName);
    }
    if (newTimezone != null) {
      timezone = validTimezone(newTimezone);
    }
    if (newActive != null) {
      active = newActive;
    }
    updatedAt = now;
  }

  public void deactivate(Instant now) {
    active = false;
    updatedAt = now;
  }

  private static String validName(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120) {
      throw new CatalogException("validation_error", "calendar name is invalid");
    }
    return value.strip();
  }

  private static String validTimezone(String value) {
    try {
      return ZoneId.of(value).getId();
    } catch (NullPointerException | ZoneRulesException exception) {
      throw new CatalogException("invalid_timezone", "timezone must be a valid IANA zone");
    }
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID collaboratorId() {
    return collaboratorId;
  }

  public String name() {
    return name;
  }

  public String timezone() {
    return timezone;
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
