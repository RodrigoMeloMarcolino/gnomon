package io.gnomon.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import io.gnomon.catalog.application.CollaboratorUseCase.CreateCollaboratorCommand;
import io.gnomon.catalog.application.port.CalendarRepository;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort;
import io.gnomon.catalog.application.port.CatalogTenantAccessPort.TenantAccess;
import io.gnomon.catalog.application.port.CollaboratorRepository;
import io.gnomon.catalog.domain.Calendar;
import io.gnomon.catalog.domain.Collaborator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CollaboratorServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CollaboratorRepository collaborators;
  @Mock private CalendarRepository calendars;
  @Mock private CatalogTenantAccessPort tenantAccess;

  private CollaboratorService service;

  @BeforeEach
  void setUp() {
    service =
        new CollaboratorService(
            collaborators, calendars, tenantAccess, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void create_whenManager_shouldPersistCollaboratorThenCalendarInSameUseCase() {
    UUID actorId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    when(tenantAccess.requireManager(actorId, "tenant"))
        .thenReturn(
            new TenantAccess(tenantId, "Tenant", "tenant", "America/Fortaleza", "BRL", "owner"));
    when(collaborators.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(calendars.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CollaboratorResult result =
        service.create(new CreateCollaboratorCommand(actorId, "tenant", "Maria"));

    assertThat(result.displayName()).isEqualTo("Maria");
    assertThat(result.calendar().collaboratorId()).isEqualTo(result.id());
    assertThat(result.calendar().timezone()).isEqualTo("America/Fortaleza");
    var ordered = inOrder(collaborators, calendars);
    ordered.verify(collaborators).save(any(Collaborator.class));
    ordered.verify(calendars).save(any(Calendar.class));
  }
}
