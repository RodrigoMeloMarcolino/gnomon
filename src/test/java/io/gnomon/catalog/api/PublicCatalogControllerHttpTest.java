package io.gnomon.catalog.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.catalog.api.controller.PublicCatalogController;
import io.gnomon.catalog.api.exception.CatalogExceptionHandler;
import io.gnomon.catalog.application.port.in.GetPublicTenantProfileUseCase;
import io.gnomon.catalog.application.port.in.ListPublicOfferingsUseCase;
import io.gnomon.catalog.application.port.in.PublicTenantProfileResult;
import io.gnomon.catalog.application.port.in.result.OfferingResult;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicCatalogControllerHttpTest {

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private GetPublicTenantProfileUseCase getPublicTenantProfile;
  @Mock private ListPublicOfferingsUseCase listPublicOfferings;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new PublicCatalogController(getPublicTenantProfile, listPublicOfferings))
            .setControllerAdvice(new CatalogExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @Test
  void getProfile_whenTenantIsActive_shouldReturnPublicFieldsOnly() throws Exception {
    when(getPublicTenantProfile.get("barbearia-solar"))
        .thenReturn(
            new PublicTenantProfileResult(
                TENANT_ID, "Barbearia Solar", "barbearia-solar", "America/Fortaleza", "BRL"));

    mockMvc
        .perform(get("/v1/public/tenants/barbearia-solar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.currency_code").value("BRL"))
        .andExpect(jsonPath("$.currencyCode").doesNotExist())
        .andExpect(jsonPath("$.role").doesNotExist())
        .andExpect(jsonPath("$.userId").doesNotExist())
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void listOfferings_withCalendarFilter_shouldPassFilterAndReturnActiveDtos() throws Exception {
    when(listPublicOfferings.list("barbearia-solar", CALENDAR_ID))
        .thenReturn(List.of(activeOffering()));

    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/offerings")
                .queryParam("calendar_id", CALENDAR_ID.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(OFFERING_ID.toString()))
        .andExpect(jsonPath("$[0].duration_minutes").value(30))
        .andExpect(jsonPath("$[0].active").doesNotExist());

    verify(listPublicOfferings).list("barbearia-solar", CALENDAR_ID);
  }

  @Test
  void getProfile_whenTenantIsInactiveOrMissing_shouldReturn404() throws Exception {
    when(getPublicTenantProfile.get("barbearia-solar"))
        .thenThrow(new CatalogException("tenant_not_found", "tenant was not found"));

    mockMvc
        .perform(get("/v1/public/tenants/barbearia-solar"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("tenant_not_found"));
  }

  private static OfferingResult activeOffering() {
    return new OfferingResult(OFFERING_ID, TENANT_ID, "Corte", null, 30, null, true, NOW, NOW);
  }
}
