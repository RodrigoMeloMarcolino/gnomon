package io.gnomon.booking.infrastructure.integration.catalog;

import io.gnomon.booking.application.port.out.StaffCalendarAccessPort;
import io.gnomon.catalog.application.port.in.StaffCalendarAccessUseCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class BookingStaffCalendarAccessAdapter implements StaffCalendarAccessPort {
  private final StaffCalendarAccessUseCase catalog;

  BookingStaffCalendarAccessAdapter(StaffCalendarAccessUseCase catalog) {
    this.catalog = catalog;
  }

  @Override
  public Optional<UUID> findCalendarIdForStaff(UUID tenantId, UUID userId) {
    return catalog.findCalendarIdForStaff(tenantId, userId);
  }
}
