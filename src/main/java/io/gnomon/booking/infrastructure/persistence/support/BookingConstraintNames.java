package io.gnomon.booking.infrastructure.persistence.support;

import java.util.Locale;

public final class BookingConstraintNames {

  private BookingConstraintNames() {}

  public static boolean contains(Throwable throwable, String constraintName) {
    String normalizedConstraint = constraintName.toLowerCase(Locale.ROOT);
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null && message.toLowerCase(Locale.ROOT).contains(normalizedConstraint)) {
        return true;
      }
    }
    return false;
  }
}
