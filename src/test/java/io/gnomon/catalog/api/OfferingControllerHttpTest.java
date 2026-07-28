package io.gnomon.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.catalog.application.CreateOfferingUseCase;
import io.gnomon.catalog.application.DeactivateOfferingUseCase;
import io.gnomon.catalog.application.GetOfferingUseCase;
import io.gnomon.catalog.application.ListOfferingsUseCase;
import io.gnomon.catalog.application.OfferingResult;
import io.gnomon.catalog.application.UpdateOfferingUseCase;
import io.gnomon.catalog.application.UpdateOfferingUseCase.UpdateOfferingCommand;
import io.gnomon.catalog.domain.CatalogException;
import io.gnomon.shared.api.GlobalExceptionHandler;
import io.gnomon.tenancy.api.security.LocalUserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OfferingControllerHttpTest {

  private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CreateOfferingUseCase createOffering;
  @Mock private ListOfferingsUseCase listOfferings;
  @Mock private GetOfferingUseCase getOffering;
  @Mock private UpdateOfferingUseCase updateOffering;
  @Mock private DeactivateOfferingUseCase deactivateOffering;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var principal =
        new LocalUserPrincipal(ACTOR_ID, "keycloak-subject", "owner@gnomon.local", "Owner");
    var controller =
        new OfferingController(
            createOffering, listOfferings, getOffering, updateOffering, deactivateOffering);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new CatalogExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new LocalUserPrincipalArgumentResolver(principal))
            .build();
  }

  @Test
  void create_whenPayloadIsValid_shouldReturn201WithoutSuccessEnvelope() throws Exception {
    when(createOffering.create(any())).thenReturn(activeOffering());

    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title":"Corte Solar",
                      "description":"Completo",
                      "durationMinutes":30,
                      "priceCents":4500
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(OFFERING_ID.toString()))
        .andExpect(jsonPath("$.durationMinutes").value(30))
        .andExpect(jsonPath("$.priceCents").value(4500))
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void create_whenDurationIsNotMultipleOf15_shouldReturn422AtBoundary() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Corte","durationMinutes":20}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"))
        .andExpect(jsonPath("$.error.details[0].field").value("durationMinutes"));
  }

  @Test
  void list_whenActorIsManager_shouldReturnOfferingsWithoutEnvelope() throws Exception {
    when(listOfferings.list(ACTOR_ID, "barbearia-solar")).thenReturn(List.of(activeOffering()));

    mockMvc
        .perform(get("/v1/tenants/barbearia-solar/offerings"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(OFFERING_ID.toString()))
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void get_whenOfferingExistsInTenant_shouldReturnOffering() throws Exception {
    when(getOffering.get(ACTOR_ID, "barbearia-solar", OFFERING_ID)).thenReturn(activeOffering());

    mockMvc
        .perform(get("/v1/tenants/barbearia-solar/offerings/{offeringId}", OFFERING_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(OFFERING_ID.toString()));
  }

  @Test
  void create_whenActiveTitleConflicts_shouldReturn422StableError() throws Exception {
    when(createOffering.create(any()))
        .thenThrow(
            new CatalogException("validation_error", "an active offering already uses this title"));

    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/offerings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Corte","durationMinutes":30}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @Test
  void update_whenNullablePriceIsExplicitlyCleared_shouldPreservePatchIntent() throws Exception {
    when(updateOffering.update(any())).thenReturn(activeOffering());

    mockMvc
        .perform(
            patch("/v1/tenants/barbearia-solar/offerings/{offeringId}", OFFERING_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"priceCents":null}
                    """))
        .andExpect(status().isOk());

    var command = ArgumentCaptor.forClass(UpdateOfferingCommand.class);
    verify(updateOffering).update(command.capture());
    assertThat(command.getValue().priceCents().specified()).isTrue();
    assertThat(command.getValue().priceCents().value()).isNull();
    assertThat(command.getValue().title().specified()).isFalse();
  }

  @Test
  void deactivate_whenOfferingExists_shouldReturn204() throws Exception {
    mockMvc
        .perform(delete("/v1/tenants/barbearia-solar/offerings/{offeringId}", OFFERING_ID))
        .andExpect(status().isNoContent());

    verify(deactivateOffering).deactivate(ACTOR_ID, "barbearia-solar", OFFERING_ID);
  }

  private static OfferingResult activeOffering() {
    return new OfferingResult(
        OFFERING_ID, TENANT_ID, "Corte Solar", "Completo", 30, 4_500, true, NOW, NOW);
  }
}
