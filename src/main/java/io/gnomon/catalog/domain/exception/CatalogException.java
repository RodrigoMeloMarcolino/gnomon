package io.gnomon.catalog.domain.exception;

public final class CatalogException extends RuntimeException {

  private final String code;

  public CatalogException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
