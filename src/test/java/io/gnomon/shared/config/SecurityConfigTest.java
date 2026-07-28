package io.gnomon.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.tenancy.application.LocalUserResult;
import io.gnomon.tenancy.application.ProvisionLocalUserUseCase;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@SpringJUnitConfig
@ContextConfiguration(classes = {SecurityConfig.class, SecurityConfigTest.TestWebConfig.class})
@WebAppConfiguration
class SecurityConfigTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void publicRoute_withoutToken_shouldBeAccessible() throws Exception {
    mockMvc.perform(get("/v1/public/test")).andExpect(status().isOk());
  }

  @Test
  void healthRoute_withoutToken_shouldBeAccessible() throws Exception {
    mockMvc.perform(get("/v1/health")).andExpect(status().isOk());
  }

  @Test
  void protectedRoute_withoutToken_shouldReturnStableErrorEnvelope() throws Exception {
    mockMvc
        .perform(get("/v1/test/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"))
        .andExpect(jsonPath("$.error.message").value("unauthorized"))
        .andExpect(jsonPath("$.error.details").doesNotExist());
  }

  @Test
  void catalogAdminRoute_withoutToken_shouldReturn401() throws Exception {
    mockMvc
        .perform(get("/v1/tenants/tenant/collaborators"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"));
  }

  @Test
  void publicCatalogRoute_withoutToken_shouldBeAccessible() throws Exception {
    mockMvc.perform(get("/v1/public/tenants/tenant/calendars")).andExpect(status().isOk());
  }

  @Test
  void protectedRoute_withJwt_shouldBeAccessible() throws Exception {
    mockMvc
        .perform(
            get("/v1/test/protected")
                .with(
                    jwt()
                        .jwt(
                            token ->
                                token
                                    .subject("keycloak-subject")
                                    .claim("email", "user@example.com")
                                    .claim("name", "Test User")
                                    .claim("email_verified", true))))
        .andExpect(status().isOk());
  }

  @Test
  void corsConfiguration_withEmptyOrigins_shouldRejectBrowserOrigins() {
    var source = new SecurityConfig().corsConfigurationSource("");
    var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/v1/public/test");

    var configuration = source.getCorsConfiguration(request);

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins()).isEmpty();
  }

  @Test
  void corsConfiguration_withConfiguredOrigins_shouldTrimAndDeduplicate() {
    var source =
        new SecurityConfig()
            .corsConfigurationSource(
                "http://localhost:3000, https://umbra.example, http://localhost:3000");
    var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/v1/public/test");

    var configuration = source.getCorsConfiguration(request);

    assertThat(configuration).isNotNull();
    assertThat(configuration.getAllowedOrigins())
        .containsExactly("http://localhost:3000", "https://umbra.example");
  }

  @Test
  void accessDeniedHandler_shouldReturnStableErrorEnvelope() throws Exception {
    var response = new org.springframework.mock.web.MockHttpServletResponse();

    new SecurityConfig()
        .accessDeniedHandler()
        .handle(
            new org.springframework.mock.web.MockHttpServletRequest(),
            response,
            new org.springframework.security.access.AccessDeniedException("denied"));

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":{\"code\":\"forbidden\",\"message\":\"forbidden\",\"details\":null}}");
  }

  @Configuration
  @EnableWebMvc
  static class TestWebConfig {

    @Bean
    TestController testController() {
      return new TestController();
    }

    @Bean
    JwtDecoder jwtDecoder() {
      return token -> {
        throw new IllegalArgumentException("Decoder must not be called by mock JWT requests");
      };
    }

    @Bean
    ProvisionLocalUserUseCase provisionLocalUserUseCase() {
      return command ->
          new LocalUserResult(
              UUID.fromString("00000000-0000-0000-0000-000000000001"),
              command.keycloakSubject(),
              command.email(),
              command.displayName());
    }

    @Bean
    ObjectMapper objectMapper() {
      return JsonMapper.builder().build();
    }
  }

  @RestController
  static class TestController {

    @GetMapping({
      "/v1/public/test",
      "/v1/public/tenants/tenant/calendars",
      "/v1/health",
      "/v1/test/protected",
      "/v1/tenants/tenant/collaborators"
    })
    String ok() {
      return "ok";
    }
  }
}
