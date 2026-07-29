package io.gnomon.booking.domain.exception;

/** Pure booking invariant failure; the application boundary maps it to a stable error contract. */
public final class BookingDomainException extends RuntimeException {

  private final String code;

  public BookingDomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
