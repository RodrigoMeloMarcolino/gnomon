package io.gnomon.booking.application.port.in;

import java.util.List;

public record AdminAppointmentPage(
    List<AdminAppointment> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {}
