package io.gnomon.catalog.application.service;

import io.gnomon.catalog.application.port.in.CreateOfferingUseCase;
import io.gnomon.catalog.application.port.in.CreateOfferingUseCase.CreateOfferingCommand;
import io.gnomon.catalog.application.port.in.DeactivateOfferingUseCase;
import io.gnomon.catalog.application.port.in.GetOfferingUseCase;
import io.gnomon.catalog.application.port.in.GetPublicTenantProfileUseCase;
import io.gnomon.catalog.application.port.in.GetPublicTenantProfileUseCase.PublicTenantProfileResult;
import io.gnomon.catalog.application.port.in.ListOfferingsUseCase;
import io.gnomon.catalog.application.port.in.ListPublicOfferingsUseCase;
import io.gnomon.catalog.application.port.in.ReplaceCalendarOfferingsUseCase;
import io.gnomon.catalog.application.port.in.ReplaceCalendarOfferingsUseCase.ReplaceCalendarOfferingsCommand;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase;
import io.gnomon.catalog.application.port.in.SchedulableOfferingUseCase.SchedulableOffering;
import io.gnomon.catalog.application.port.in.UpdateOfferingUseCase;
import io.gnomon.catalog.application.port.in.UpdateOfferingUseCase.UpdateOfferingCommand;
import io.gnomon.catalog.application.port.in.result.OfferingResult;
import io.gnomon.catalog.application.port.out.CalendarOfferingRepository;
import io.gnomon.catalog.application.port.out.CalendarRepository;
import io.gnomon.catalog.application.port.out.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.out.OfferingRepository;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.catalog.domain.model.Calendar;
import io.gnomon.catalog.domain.model.Offering;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OfferingService
    implements CreateOfferingUseCase,
        ListOfferingsUseCase,
        GetOfferingUseCase,
        UpdateOfferingUseCase,
        DeactivateOfferingUseCase,
        ReplaceCalendarOfferingsUseCase,
        ListPublicOfferingsUseCase,
        GetPublicTenantProfileUseCase,
        SchedulableOfferingUseCase {

  private final CatalogTenantAccessPort access;
  private final CalendarRepository calendars;
  private final OfferingRepository offerings;
  private final CalendarOfferingRepository assignments;
  private final Clock clock;

  @Autowired
  public OfferingService(
      CatalogTenantAccessPort access,
      CalendarRepository calendars,
      OfferingRepository offerings,
      CalendarOfferingRepository assignments) {
    this(access, calendars, offerings, assignments, Clock.systemUTC());
  }

  public OfferingService(
      CatalogTenantAccessPort access,
      CalendarRepository calendars,
      OfferingRepository offerings,
      CalendarOfferingRepository assignments,
      Clock clock) {
    this.access = access;
    this.calendars = calendars;
    this.offerings = offerings;
    this.assignments = assignments;
    this.clock = clock;
  }

  @Override
  @Transactional
  public OfferingResult create(CreateOfferingCommand command) {
    var tenant = access.requireManager(command.actorUserId(), command.tenantSlug());
    Offering offering =
        Offering.create(
            tenant.tenantId(),
            command.title(),
            command.description(),
            command.durationMinutes(),
            command.priceCents(),
            clock.instant());
    rejectDuplicateActiveTitle(offering);
    return OfferingResult.from(offerings.save(offering));
  }

  @Override
  public List<OfferingResult> list(UUID actorUserId, String tenantSlug) {
    var tenant = access.requireManager(actorUserId, tenantSlug);
    return sorted(offerings.findByTenantId(tenant.tenantId()));
  }

  @Override
  public OfferingResult get(UUID actorUserId, String tenantSlug, UUID offeringId) {
    var tenant = access.requireManager(actorUserId, tenantSlug);
    return OfferingResult.from(requireAdministrativeOffering(tenant.tenantId(), offeringId));
  }

  @Override
  @Transactional
  public OfferingResult update(UpdateOfferingCommand command) {
    var tenant = access.requireManager(command.actorUserId(), command.tenantSlug());
    Offering offering = requireAdministrativeOffering(tenant.tenantId(), command.offeringId());
    offering.update(
        command.title(),
        command.description(),
        command.durationMinutes(),
        command.priceCents(),
        command.active(),
        clock.instant());
    rejectDuplicateActiveTitle(offering);
    return OfferingResult.from(offerings.save(offering));
  }

  @Override
  @Transactional
  public void deactivate(UUID actorUserId, String tenantSlug, UUID offeringId) {
    var tenant = access.requireManager(actorUserId, tenantSlug);
    Offering offering = requireAdministrativeOffering(tenant.tenantId(), offeringId);
    offering.deactivate(clock.instant());
    offerings.save(offering);
  }

  @Override
  @Transactional
  public List<OfferingResult> replace(ReplaceCalendarOfferingsCommand command) {
    if (command.offeringIds() == null) {
      throw new CatalogException("validation_error", "offeringIds is required");
    }
    var tenant = access.requireManager(command.actorUserId(), command.tenantSlug());
    requireAdministrativeCalendar(tenant.tenantId(), command.calendarId());
    Set<UUID> offeringIds = Set.copyOf(command.offeringIds());
    List<Offering> selected =
        offeringIds.stream()
            .map(id -> requireAdministrativeOffering(tenant.tenantId(), id))
            .toList();
    assignments.replace(tenant.tenantId(), command.calendarId(), offeringIds);
    return sorted(selected);
  }

  @Override
  public List<OfferingResult> list(String tenantSlug, UUID calendarId) {
    var tenant = access.requirePublicTenant(tenantSlug);
    if (calendarId != null) {
      Calendar calendar =
          calendars
              .findByTenantIdAndId(tenant.tenantId(), calendarId)
              .filter(Calendar::active)
              .orElseThrow(
                  () -> new CatalogException("calendar_not_found", "calendar was not found"));
    }
    return sorted(offerings.findActiveByTenantId(tenant.tenantId(), calendarId));
  }

  @Override
  public PublicTenantProfileResult get(String tenantSlug) {
    var tenant = access.requirePublicTenant(tenantSlug);
    return new PublicTenantProfileResult(
        tenant.tenantId(),
        tenant.name(),
        tenant.slug(),
        tenant.defaultTimezone(),
        tenant.currencyCode());
  }

  @Override
  public SchedulableOffering requireSchedulableOffering(
      String tenantSlug, UUID calendarId, UUID offeringId) {
    var tenant = access.requirePublicTenant(tenantSlug);
    Calendar calendar =
        calendars
            .findByTenantIdAndId(tenant.tenantId(), calendarId)
            .filter(Calendar::active)
            .orElseThrow(
                () -> new CatalogException("calendar_not_found", "calendar was not found"));
    Offering offering =
        offerings.findActiveByTenantId(tenant.tenantId(), calendarId).stream()
            .filter(candidate -> candidate.id().equals(offeringId))
            .findFirst()
            .orElseThrow(
                () -> new CatalogException("offering_not_found", "offering was not found"));
    return new SchedulableOffering(
        tenant.tenantId(),
        calendar.id(),
        calendar.name(),
        java.time.ZoneId.of(calendar.timezone()),
        offering.id(),
        offering.title(),
        offering.durationMinutes(),
        offering.priceCents());
  }

  private Offering requireAdministrativeOffering(UUID tenantId, UUID offeringId) {
    return offerings
        .findByTenantIdAndId(tenantId, offeringId)
        .orElseGet(
            () -> {
              if (offerings.findById(offeringId).isPresent()) {
                throw new CatalogException(
                    "catalog_access_denied", "cross-tenant access is forbidden");
              }
              throw new CatalogException("offering_not_found", "offering was not found");
            });
  }

  private Calendar requireAdministrativeCalendar(UUID tenantId, UUID calendarId) {
    return calendars
        .findByTenantIdAndId(tenantId, calendarId)
        .orElseGet(
            () -> {
              if (calendars.findById(calendarId).isPresent()) {
                throw new CatalogException(
                    "catalog_access_denied", "cross-tenant access is forbidden");
              }
              throw new CatalogException("calendar_not_found", "calendar was not found");
            });
  }

  private void rejectDuplicateActiveTitle(Offering offering) {
    if (offering.active()
        && offerings.activeTitleExists(
            offering.tenantId(), offering.normalizedTitle(), offering.id())) {
      throw new CatalogException("validation_error", "an active offering already uses this title");
    }
  }

  private static List<OfferingResult> sorted(List<Offering> values) {
    return values.stream()
        .sorted(Comparator.comparing(Offering::title).thenComparing(Offering::id))
        .map(OfferingResult::from)
        .toList();
  }
}
