package io.gnomon.catalog.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.catalog.application.ReplaceCalendarOfferingsUseCase;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.shared.api.GlobalExceptionHandler;
import io.gnomon.tenancy.api.security.LocalUserPrincipal;
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
class CalendarOfferingControllerHttpTest {

  private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

  @Mock private ReplaceCalendarOfferingsUseCase replaceCalendarOfferings;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var principal =
        new LocalUserPrincipal(ACTOR_ID, "keycloak-subject", "owner@gnomon.local", "Owner");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new CalendarOfferingController(replaceCalendarOfferings))
            .setControllerAdvice(new CatalogExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new LocalUserPrincipalArgumentResolver(principal))
            .build();
  }

  @Test
  void replace_whenCalendarIsCrossTenant_shouldReturn403() throws Exception {
    when(replaceCalendarOfferings.replace(any()))
        .thenThrow(
            new CatalogException("catalog_access_denied", "cross-tenant access is forbidden"));

    mockMvc
        .perform(
            put("/v1/tenants/barbearia-solar/calendars/{calendarId}/offerings", CALENDAR_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"offeringIds":["%s"]}
                    """
                        .formatted(OFFERING_ID)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("catalog_access_denied"));
  }
}
