package io.gnomon.catalog.infrastructure.persistence;

import io.gnomon.catalog.application.port.CalendarOfferingRepository;
import io.gnomon.catalog.domain.CatalogException;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class CalendarOfferingPersistenceAdapter implements CalendarOfferingRepository {

  private final SpringDataCalendarOfferingRepository repository;
  private final Clock clock;

  CalendarOfferingPersistenceAdapter(SpringDataCalendarOfferingRepository repository) {
    this(repository, Clock.systemUTC());
  }

  CalendarOfferingPersistenceAdapter(SpringDataCalendarOfferingRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void replace(UUID tenantId, UUID calendarId, Set<UUID> offeringIds) {
    try {
      repository.deleteAssignments(tenantId, calendarId);
      if (offeringIds.isEmpty()) {
        return;
      }
      var createdAt = clock.instant();
      var entities =
          offeringIds.stream()
              .map(
                  offeringId ->
                      new CalendarOfferingJpaEntity(tenantId, calendarId, offeringId, createdAt))
              .toList();
      repository.saveAllAndFlush(entities);
    } catch (DataIntegrityViolationException exception) {
      if (isKnownAssignmentConstraint(exception)) {
        throw new CatalogException(
            "validation_error", "calendar offering assignment is no longer valid");
      }
      throw exception;
    }
  }

  private static boolean isKnownAssignmentConstraint(Throwable exception) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      String message = current.getMessage();
      if (message != null
          && (message.contains("fk_calendar_offerings_tenant")
              || message.contains("fk_calendar_offerings_tenant_calendar")
              || message.contains("fk_calendar_offerings_tenant_offering")
              || message.contains("pk_calendar_offerings"))) {
        return true;
      }
    }
    return false;
  }
}
