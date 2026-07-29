package io.gnomon.tenancy.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import io.gnomon.tenancy.api.controller.MembershipController;
import io.gnomon.tenancy.api.exception.TenancyExceptionHandler;
import io.gnomon.tenancy.application.port.in.ManageMembershipUseCase;
import io.gnomon.tenancy.application.port.in.RemoveMembershipCommand;
import io.gnomon.tenancy.application.port.in.result.MembershipResult;
import io.gnomon.tenancy.domain.exception.TenancyException;
import java.time.Instant;
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
class MembershipControllerHttpTest {

  private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID MEMBERSHIP_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final Instant NOW = Instant.parse("2026-07-28T18:00:00Z");

  @Mock private ManageMembershipUseCase manageMembership;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    var principal =
        new LocalUserPrincipal(ACTOR_ID, "keycloak-subject", "owner@gnomon.local", "Owner");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new MembershipController(manageMembership))
            .setControllerAdvice(new TenancyExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new LocalUserPrincipalArgumentResolver(principal))
            .build();
  }

  @Test
  void add_withValidRequest_shouldReturn201WithoutSuccessEnvelope() throws Exception {
    when(manageMembership.add(any())).thenReturn(adminMembership());

    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"admin@gnomon.local","role":"admin"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()))
        .andExpect(jsonPath("$.email").value("admin@gnomon.local"))
        .andExpect(jsonPath("$.role").value("admin"))
        .andExpect(jsonPath("$.success").doesNotExist());
  }

  @Test
  void add_withStaffRole_shouldReturn422ApplicationError() throws Exception {
    when(manageMembership.add(any()))
        .thenThrow(
            new TenancyException(
                "staff_requires_collaborator", "staff membership requires a linked collaborator"));

    mockMvc
        .perform(
            post("/v1/tenants/barbearia-solar/memberships")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"staff@gnomon.local","role":"staff"}
                    """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("staff_requires_collaborator"));
  }

  @Test
  void changeRole_withValidRequest_shouldReturn200() throws Exception {
    when(manageMembership.changeRole(any())).thenReturn(adminMembership());

    mockMvc
        .perform(
            patch("/v1/tenants/barbearia-solar/memberships/{membershipId}", MEMBERSHIP_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"role":"admin"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("admin"));
  }

  @Test
  void remove_withExistingMembership_shouldReturn204() throws Exception {
    mockMvc
        .perform(delete("/v1/tenants/barbearia-solar/memberships/{membershipId}", MEMBERSHIP_ID))
        .andExpect(status().isNoContent());

    verify(manageMembership)
        .remove(new RemoveMembershipCommand(ACTOR_ID, "barbearia-solar", MEMBERSHIP_ID));
  }

  private static MembershipResult adminMembership() {
    return new MembershipResult(
        MEMBERSHIP_ID, TENANT_ID, USER_ID, "admin@gnomon.local", "Admin", "admin", NOW, NOW);
  }
}
