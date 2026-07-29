package io.gnomon.booking.application.exception;

public final class BookingException extends RuntimeException {

  private final String code;

  public BookingException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
