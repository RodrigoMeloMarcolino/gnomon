package io.gnomon.tenancy.domain.exception;

/** Stable domain/application error exposed to the HTTP exception translator. */
public final class TenancyException extends RuntimeException {

  private final String code;

  public TenancyException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
