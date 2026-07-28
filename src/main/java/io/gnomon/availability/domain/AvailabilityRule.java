package io.gnomon.availability.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

public final class AvailabilityRule {

  private final UUID id;
  private final UUID tenantId;
  private final UUID calendarId;
  private DayOfWeek weekday;
  private LocalTime startTime;
  private LocalTime endTime;
  private boolean active;
  private final Instant createdAt;
  private Instant updatedAt;

  public AvailabilityRule(
      UUID id,
      UUID tenantId,
      UUID calendarId,
      DayOfWeek weekday,
      LocalTime startTime,
      LocalTime endTime,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id);
    this.tenantId = Objects.requireNonNull(tenantId);
    this.calendarId = Objects.requireNonNull(calendarId);
    AvailabilityWindow window = new AvailabilityWindow(weekday, startTime, endTime, active);
    this.weekday = window.weekday();
    this.startTime = window.startTime();
    this.endTime = window.endTime();
    this.active = window.active();
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);
  }

  public static AvailabilityRule create(
      UUID tenantId,
      UUID calendarId,
      DayOfWeek weekday,
      LocalTime startTime,
      LocalTime endTime,
      Instant now) {
    return new AvailabilityRule(
        UUID.randomUUID(), tenantId, calendarId, weekday, startTime, endTime, true, now, now);
  }

  public void update(
      DayOfWeek newWeekday,
      LocalTime newStartTime,
      LocalTime newEndTime,
      Boolean newActive,
      Instant now) {
    AvailabilityWindow window =
        new AvailabilityWindow(
            newWeekday == null ? weekday : newWeekday,
            newStartTime == null ? startTime : newStartTime,
            newEndTime == null ? endTime : newEndTime,
            newActive == null ? active : newActive);
    weekday = window.weekday();
    startTime = window.startTime();
    endTime = window.endTime();
    active = window.active();
    updatedAt = Objects.requireNonNull(now);
  }

  public void deactivate(Instant now) {
    active = false;
    updatedAt = Objects.requireNonNull(now);
  }

  public AvailabilityWindow toWindow() {
    return new AvailabilityWindow(weekday, startTime, endTime, active);
  }

  public UUID id() {
    return id;
  }

  public UUID tenantId() {
    return tenantId;
  }

  public UUID calendarId() {
    return calendarId;
  }

  public DayOfWeek weekday() {
    return weekday;
  }

  public LocalTime startTime() {
    return startTime;
  }

  public LocalTime endTime() {
    return endTime;
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
