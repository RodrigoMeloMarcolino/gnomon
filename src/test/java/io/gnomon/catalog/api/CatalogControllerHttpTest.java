package io.gnomon.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.catalog.api.controller.CalendarController;
import io.gnomon.catalog.api.controller.CollaboratorController;
import io.gnomon.catalog.api.controller.PublicCalendarController;
import io.gnomon.catalog.application.port.in.CalendarUseCase;
import io.gnomon.catalog.application.port.in.CollaboratorUseCase;
import io.gnomon.catalog.application.port.in.PublicCalendarUseCase;
import io.gnomon.catalog.application.port.in.result.CalendarResult;
import io.gnomon.catalog.application.port.in.result.CollaboratorResult;
import io.gnomon.catalog.application.port.in.result.PublicCalendarResult;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class CatalogControllerHttpTest {

  @Mock private CollaboratorUseCase collaborators;
  @Mock private CalendarUseCase calendars;
  @Mock private PublicCalendarUseCase publicCalendars;

  private MockMvc mockMvc;
  private LocalUserPrincipal principal;

  @BeforeEach
  void setUp() {
    principal = new LocalUserPrincipal(UUID.randomUUID(), "subject", "owner@example.com", "Owner");
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new CollaboratorController(collaborators),
                new CalendarController(calendars),
                new PublicCalendarController(publicCalendars))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new PrincipalResolver(principal))
            .build();
  }

  @Test
  void create_withValidRequest_shouldReturn201() throws Exception {
    when(collaborators.create(any())).thenReturn(collaborator());

    mockMvc
        .perform(
            post("/v1/tenants/tenant/collaborators")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Maria\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.displayName").value("Maria"))
        .andExpect(jsonPath("$.calendar.timezone").value("America/Fortaleza"));
  }

  @Test
  void create_withBlankName_shouldReturn422() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/tenant/collaborators")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @Test
  void publicCalendars_shouldNotExposeUserId() throws Exception {
    UUID calendarId = UUID.randomUUID();
    when(publicCalendars.listActive("tenant"))
        .thenReturn(
            List.of(
                new PublicCalendarResult(
                    calendarId, UUID.randomUUID(), "Maria", "Agenda", "America/Fortaleza")));

    mockMvc
        .perform(get("/v1/public/tenants/tenant/calendars"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(calendarId.toString()))
        .andExpect(jsonPath("$[0].collaborator_name").value("Maria"))
        .andExpect(jsonPath("$[0].userId").doesNotExist());
  }

  @Test
  void deactivateCollaborator_shouldUseAuthenticatedActorAndReturn204() throws Exception {
    UUID collaboratorId = UUID.randomUUID();

    mockMvc
        .perform(delete("/v1/tenants/tenant/collaborators/{id}", collaboratorId))
        .andExpect(status().isNoContent());

    verify(collaborators).deactivate(principal.userId(), "tenant", collaboratorId);
  }

  @Test
  void getCalendar_shouldUseAuthenticatedActor() throws Exception {
    CalendarResult result = collaborator().calendar();
    when(calendars.get(principal.userId(), "tenant", result.id())).thenReturn(result);

    mockMvc
        .perform(get("/v1/tenants/tenant/calendars/{id}", result.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(result.id().toString()));

    verify(calendars).get(principal.userId(), "tenant", result.id());
  }

  private static CollaboratorResult collaborator() {
    UUID tenantId = UUID.randomUUID();
    UUID collaboratorId = UUID.randomUUID();
    Instant now = Instant.parse("2026-07-28T18:00:00Z");
    return new CollaboratorResult(
        collaboratorId,
        tenantId,
        null,
        "Maria",
        true,
        new CalendarResult(
            UUID.randomUUID(),
            tenantId,
            collaboratorId,
            "Maria",
            "America/Fortaleza",
            true,
            now,
            now),
        now,
        now);
  }

  private record PrincipalResolver(LocalUserPrincipal principal)
      implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
    }

    @Override
    public Object resolveArgument(
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory) {
      return principal;
    }
  }
}
