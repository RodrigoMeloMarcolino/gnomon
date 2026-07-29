package io.gnomon.customers.domain.exception;

/** Signals a violated invariant of the global customer aggregate. */
public class CustomerException extends RuntimeException {

  private final String code;

  public CustomerException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
