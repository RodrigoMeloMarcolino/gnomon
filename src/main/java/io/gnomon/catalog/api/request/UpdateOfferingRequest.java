package io.gnomon.catalog.api.request;

import io.gnomon.catalog.api.validation.PositiveMultipleOf15;
import io.gnomon.catalog.domain.model.Offering.Change;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * PATCH request that preserves the distinction between an absent property and an explicit JSON
 * {@code null}, allowing nullable description and price fields to be cleared.
 */
public final class UpdateOfferingRequest {

  private boolean titleSpecified;
  private boolean descriptionSpecified;
  private boolean durationMinutesSpecified;
  private boolean priceCentsSpecified;
  private boolean activeSpecified;

  @Size(min = 1, max = 120)
  private String title;

  private String description;

  @PositiveMultipleOf15 private Integer durationMinutes;

  @PositiveOrZero private Integer priceCents;
  private Boolean active;

  public void setTitle(String title) {
    titleSpecified = true;
    this.title = title;
  }

  public void setDescription(String description) {
    descriptionSpecified = true;
    this.description = description;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    durationMinutesSpecified = true;
    this.durationMinutes = durationMinutes;
  }

  public void setPriceCents(Integer priceCents) {
    priceCentsSpecified = true;
    this.priceCents = priceCents;
  }

  public void setActive(Boolean active) {
    activeSpecified = true;
    this.active = active;
  }

  @AssertTrue(message = "at least one field must be provided")
  public boolean isAnyFieldSpecified() {
    return titleSpecified
        || descriptionSpecified
        || durationMinutesSpecified
        || priceCentsSpecified
        || activeSpecified;
  }

  public Change<String> titleChange() {
    return titleSpecified ? Change.to(title) : Change.unchanged();
  }

  public Change<String> descriptionChange() {
    return descriptionSpecified ? Change.to(description) : Change.unchanged();
  }

  public Change<Integer> durationMinutesChange() {
    return durationMinutesSpecified ? Change.to(durationMinutes) : Change.unchanged();
  }

  public Change<Integer> priceCentsChange() {
    return priceCentsSpecified ? Change.to(priceCents) : Change.unchanged();
  }

  public Change<Boolean> activeChange() {
    return activeSpecified ? Change.to(active) : Change.unchanged();
  }
}
