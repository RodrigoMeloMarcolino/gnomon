package io.gnomon.booking.application.port.in;

public interface CreateAppointmentUseCase {

  CreationResult create(CreateAppointmentCommand command);
}
