package io.gnomon.tenancy.infrastructure.persistence.support;

public final class ConstraintNames {

  private ConstraintNames() {}

  public static boolean contains(Throwable throwable, String constraintName) {
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null && current.getMessage().contains(constraintName)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
