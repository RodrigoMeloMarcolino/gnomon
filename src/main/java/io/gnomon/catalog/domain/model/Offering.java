package io.gnomon.catalog.domain.model;

import io.gnomon.catalog.domain.exception.CatalogException;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Tenant-owned service that can be offered by one or more calendars. */
public final class Offering {

  private final UUID id;
  private final UUID tenantId;
  private String title;
  private String description;
  private int durationMinutes;
  private Integer priceCents;
  private boolean active;
  private final Instant createdAt;
  private Instant updatedAt;

  public Offering(
      UUID id,
      UUID tenantId,
      String title,
      String description,
      int durationMinutes,
      Integer priceCents,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
    this.title = validTitle(title);
    this.description = normalizedDescription(description);
    this.durationMinutes = validDuration(durationMinutes);
    this.priceCents = validPrice(priceCents);
    this.active = active;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Offering create(
      UUID tenantId,
      String title,
      String description,
      int durationMinutes,
      Integer priceCents,
      Instant now) {
    return new Offering(
        UUID.randomUUID(),
        tenantId,
        title,
        description,
        durationMinutes,
        priceCents,
        true,
        now,
        now);
  }

  public void update(
      Change<String> title,
      Change<String> description,
      Change<Integer> durationMinutes,
      Change<Integer> priceCents,
      Change<Boolean> active,
      Instant now) {
    if (title.specified()) {
      this.title = validTitle(title.value());
    }
    if (description.specified()) {
      this.description = normalizedDescription(description.value());
    }
    if (durationMinutes.specified()) {
      if (durationMinutes.value() == null) {
        throw validation("offering duration is required");
      }
      this.durationMinutes = validDuration(durationMinutes.value());
    }
    if (priceCents.specified()) {
      this.priceCents = validPrice(priceCents.value());
    }
    if (active.specified()) {
      if (active.value() == null) {
        throw validation("offering active flag is required");
      }
      this.active = active.value();
    }
    this.updatedAt = Objects.requireNonNull(now, "now");
  }

  public void deactivate(Instant now) {
    active = false;
    updatedAt = Objects.requireNonNull(now, "now");
  }

  public String normalizedTitle() {
    return title.toLowerCase(Locale.ROOT);
  }

  private static String validTitle(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120) {
      throw validation("offering title is invalid");
    }
    return value.strip();
  }

  private static String normalizedDescription(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.strip();
    return normalized.isEmpty() ? null : normalized;
  }

  private static int validDuration(int value) {
    if (value <= 0 || value % 15 != 0) {
      throw validation("offering duration must be a positive multiple of 15");
    }
    return value;
  }

  private static Integer validPrice(Integer value) {
    if (value != null && value < 0) {
      throw validation("offering price must not be negative");
    }
    return value;
  }

  private static CatalogException validation(String message) {
    return new CatalogException("validation_error", message);
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public int durationMinutes() {
    return durationMinutes;
  }

  public Integer priceCents() {
    return priceCents;
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

  public record Change<T>(boolean specified, T value) {

    public static <T> Change<T> unchanged() {
      return new Change<>(false, null);
    }

    public static <T> Change<T> to(T value) {
      return new Change<>(true, value);
    }
  }
}
