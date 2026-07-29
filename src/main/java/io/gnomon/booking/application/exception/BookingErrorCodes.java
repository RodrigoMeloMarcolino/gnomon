package io.gnomon.booking.application.exception;

/** Stable application error contracts; HTTP adapters must not depend on service implementations. */
public final class BookingErrorCodes {

  public static final String SLOT_UNAVAILABLE_VALIDATION = "slot_unavailable_validation";

  private BookingErrorCodes() {}
}
