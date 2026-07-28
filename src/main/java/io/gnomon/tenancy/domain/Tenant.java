package io.gnomon.tenancy.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Tenant {

  private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
  private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

  private final UUID id;
  private String name;
  private final String slug;
  private String timezone;
  private String currencyCode;
  private TenantStatus status;
  private final Instant createdAt;
  private Instant updatedAt;

  public Tenant(
      UUID id,
      String name,
      String slug,
      String timezone,
      String currencyCode,
      TenantStatus status,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = validName(name);
    this.slug = validSlug(slug);
    this.timezone = validTimezone(timezone);
    this.currencyCode = validCurrency(currencyCode);
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static Tenant create(
      String name, String slug, String timezone, String currencyCode, Instant now) {
    return new Tenant(
        UUID.randomUUID(),
        name,
        slug,
        timezone,
        currencyCode == null ? "BRL" : currencyCode,
        TenantStatus.ACTIVE,
        now,
        now);
  }

  public void update(
      String newName,
      String newTimezone,
      String newCurrencyCode,
      TenantStatus newStatus,
      Instant now) {
    if (newName != null) {
      name = validName(newName);
    }
    if (newTimezone != null) {
      timezone = validTimezone(newTimezone);
    }
    if (newCurrencyCode != null) {
      currencyCode = validCurrency(newCurrencyCode);
    }
    if (newStatus != null) {
      status = newStatus;
    }
    updatedAt = Objects.requireNonNull(now, "now");
  }

  private static String validName(String value) {
    if (value == null || value.isBlank() || value.strip().length() > 120) {
      throw new TenancyException("validation_error", "tenant name is invalid");
    }
    return value.strip();
  }

  private static String validSlug(String value) {
    if (value == null
        || value.length() > 80
        || !value.equals(value.toLowerCase(Locale.ROOT))
        || !SLUG.matcher(value).matches()) {
      throw new TenancyException("validation_error", "tenant slug is invalid");
    }
    return value;
  }

  private static String validTimezone(String value) {
    try {
      return ZoneId.of(value).getId();
    } catch (NullPointerException | ZoneRulesException exception) {
      throw new TenancyException("invalid_timezone", "timezone must be a valid IANA zone");
    }
  }

  private static String validCurrency(String value) {
    if (value == null || !CURRENCY.matcher(value).matches()) {
      throw new TenancyException("validation_error", "currency code must be ISO 4217");
    }
    return value;
  }

  public UUID id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String slug() {
    return slug;
  }

  public String timezone() {
    return timezone;
  }

  public String currencyCode() {
    return currencyCode;
  }

  public TenantStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public enum TenantStatus {
    ACTIVE,
    SUSPENDED;

    public static TenantStatus from(String value) {
      if (value == null) {
        return null;
      }
      try {
        return valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException exception) {
        throw new TenancyException("validation_error", "tenant status is invalid");
      }
    }

    public String databaseValue() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
