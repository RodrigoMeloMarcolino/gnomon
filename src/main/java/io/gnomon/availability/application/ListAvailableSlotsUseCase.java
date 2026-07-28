package io.gnomon.availability.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ListAvailableSlotsUseCase {

  List<Instant> list(String tenantSlug, UUID calendarId, UUID offeringId, LocalDate date);
}
