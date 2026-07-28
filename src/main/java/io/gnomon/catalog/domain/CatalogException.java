package io.gnomon.catalog.domain;

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
