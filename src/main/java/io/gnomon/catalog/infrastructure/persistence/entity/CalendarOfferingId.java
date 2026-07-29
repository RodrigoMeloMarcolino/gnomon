package io.gnomon.catalog.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class CalendarOfferingId implements Serializable {

  @Column(name = "calendar_id", nullable = false, updatable = false)
  private UUID calendarId;

  @Column(name = "offering_id", nullable = false, updatable = false)
  private UUID offeringId;

  protected CalendarOfferingId() {}

  CalendarOfferingId(UUID calendarId, UUID offeringId) {
    this.calendarId = Objects.requireNonNull(calendarId, "calendarId");
    this.offeringId = Objects.requireNonNull(offeringId, "offeringId");
  }

  UUID calendarId() {
    return calendarId;
  }

  UUID offeringId() {
    return offeringId;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof CalendarOfferingId that)) {
      return false;
    }
    return Objects.equals(calendarId, that.calendarId)
        && Objects.equals(offeringId, that.offeringId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(calendarId, offeringId);
  }
}
