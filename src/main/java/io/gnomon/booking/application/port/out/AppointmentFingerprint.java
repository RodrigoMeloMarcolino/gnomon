package io.gnomon.booking.application.port.out;

public interface AppointmentFingerprint {

  String sha256(NormalizedBooking booking);
}
