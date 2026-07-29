package io.gnomon.availability.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.availability.api.controller.AvailabilityRuleController;
import io.gnomon.availability.api.exception.AvailabilityExceptionHandler;
import io.gnomon.availability.application.port.in.AvailabilityRuleUseCase;
import io.gnomon.availability.application.port.in.result.AvailabilityRuleResult;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AvailabilityRuleControllerHttpTest {

  private static final UUID ACTOR_ID = UUID.randomUUID();
  private static final UUID CALENDAR_ID = UUID.randomUUID();
  private static final UUID RULE_ID = UUID.randomUUID();

  @Mock private AvailabilityRuleUseCase rules;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var principal = new LocalUserPrincipal(ACTOR_ID, "sub", "owner@test", "Owner");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AvailabilityRuleController(rules))
            .setControllerAdvice(new AvailabilityExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new LocalUserPrincipalArgumentResolver(principal))
            .build();
  }

  @Test
  void create_whenPayloadIsAligned_shouldReturn201() throws Exception {
    when(rules.create(any())).thenReturn(result(true));

    mockMvc
        .perform(
            post("/v1/tenants/tenant/calendars/{calendarId}/availability-rules", CALENDAR_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"weekday":1,"startTime":"09:00:00","endTime":"12:00:00"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(RULE_ID.toString()))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void create_whenWeekdayIsInvalid_shouldReturn422AtBoundary() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/tenant/calendars/{calendarId}/availability-rules", CALENDAR_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"weekday":8,"startTime":"09:00:00","endTime":"12:00:00"}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"))
        .andExpect(jsonPath("$.error.details[0].field").value("weekday"));
  }

  @Test
  void deactivate_shouldReturn204AndInvokeSoftDeactivateUseCase() throws Exception {
    mockMvc
        .perform(
            delete(
                "/v1/tenants/tenant/calendars/{calendarId}/availability-rules/{ruleId}",
                CALENDAR_ID,
                RULE_ID))
        .andExpect(status().isNoContent());

    verify(rules).deactivate(ACTOR_ID, "tenant", CALENDAR_ID, RULE_ID);
  }

  private static AvailabilityRuleResult result(boolean active) {
    Instant now = Instant.parse("2027-07-01T12:00:00Z");
    return new AvailabilityRuleResult(
        RULE_ID, CALENDAR_ID, 1, LocalTime.of(9, 0), LocalTime.of(12, 0), active, now, now);
  }
}
