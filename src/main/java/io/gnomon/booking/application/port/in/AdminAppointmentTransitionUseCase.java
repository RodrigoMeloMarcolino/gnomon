package io.gnomon.booking.application.port.in;

import java.util.UUID;

public interface AdminAppointmentTransitionUseCase {
  AdminAppointment transition(
      UUID actorUserId, String tenantSlug, UUID id, Transition transition);

  enum Transition {
    CANCEL,
    COMPLETE,
    NO_SHOW
  }
}
