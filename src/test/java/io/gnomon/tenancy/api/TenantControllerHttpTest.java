package io.gnomon.tenancy.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.shared.api.GlobalExceptionHandler;
import io.gnomon.tenancy.api.security.LocalUserPrincipal;
import io.gnomon.tenancy.application.CreateTenantUseCase;
import io.gnomon.tenancy.application.CreateTenantUseCase.CreateTenantCommand;
import io.gnomon.tenancy.application.GetTenantUseCase;
import io.gnomon.tenancy.application.ListMyTenantsUseCase;
import io.gnomon.tenancy.application.TenantResult;
import io.gnomon.tenancy.application.UpdateTenantUseCase;
import io.gnomon.tenancy.domain.TenancyException;
import java.time.Instant;
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
class TenantControllerHttpTest {

  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private CreateTenantUseCase createTenant;
  @Mock private ListMyTenantsUseCase listMyTenants;
  @Mock private GetTenantUseCase getTenant;
  @Mock private UpdateTenantUseCase updateTenant;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var principal =
        new LocalUserPrincipal(USER_ID, "keycloak-subject", "owner@gnomon.local", "Owner");
    var controller = new TenantController(createTenant, listMyTenants, getTenant, updateTenant);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new TenancyExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new LocalUserPrincipalArgumentResolver(principal))
            .build();
  }

  @Test
  void create_withValidRequest_shouldReturn201WithoutSuccessEnvelope() throws Exception {
    when(createTenant.create(any())).thenReturn(ownerTenant());

    mockMvc
        .perform(
            post("/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name":"Barbearia Solar",
                      "slug":"barbearia-solar",
                      "timezone":"America/Fortaleza",
                      "currencyCode":"BRL"
                    }
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(TENANT_ID.toString()))
        .andExpect(jsonPath("$.slug").value("barbearia-solar"))
        .andExpect(jsonPath("$.role").value("owner"))
        .andExpect(jsonPath("$.success").doesNotExist());

    var command = ArgumentCaptor.forClass(CreateTenantCommand.class);
    verify(createTenant).create(command.capture());
    org.assertj.core.api.Assertions.assertThat(command.getValue())
        .isEqualTo(
            new CreateTenantCommand(
                USER_ID, "Barbearia Solar", "barbearia-solar", "America/Fortaleza", "BRL"));
  }

  @Test
  void create_withInvalidRequest_shouldReturn422ErrorEnvelope() throws Exception {
    mockMvc
        .perform(
            post("/v1/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"","slug":"Invalid Slug","timezone":"","currencyCode":"brl"}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"))
        .andExpect(jsonPath("$.error.details").isArray())
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void get_whenActorHasNoMembership_shouldReturn403StableError() throws Exception {
    when(getTenant.get(USER_ID, "barbearia-solar"))
        .thenThrow(new TenancyException("membership_required", "membership is required"));

    mockMvc
        .perform(get("/v1/tenants/barbearia-solar"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("membership_required"))
        .andExpect(jsonPath("$.error.message").value("membership is required"));
  }

  private static TenantResult ownerTenant() {
    return new TenantResult(
        TENANT_ID,
        "Barbearia Solar",
        "barbearia-solar",
        "America/Fortaleza",
        "BRL",
        "active",
        "owner",
        NOW,
        NOW);
  }
}
