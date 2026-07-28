package io.gnomon.booking.domain;

public interface AppointmentFingerprint {

  String sha256(NormalizedBooking booking);

  record NormalizedBooking(
      String calendarId,
      String offeringId,
      String startAt,
      String customerName,
      String customerPhone,
      String customerEmail,
      String customerNotes) {}
}
