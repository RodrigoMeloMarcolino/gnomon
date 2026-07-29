package io.gnomon.availability.domain.exception;

public final class AvailabilityException extends RuntimeException {

  private final String code;

  public AvailabilityException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
