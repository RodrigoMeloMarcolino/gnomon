package io.gnomon.booking.infrastructure.persistence;

import java.util.Locale;

final class BookingConstraintNames {

  private BookingConstraintNames() {}

  static boolean contains(Throwable throwable, String constraintName) {
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
