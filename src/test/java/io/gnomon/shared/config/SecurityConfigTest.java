package io.gnomon.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.gnomon.shared.logging.RequestCorrelationFilter;
import io.gnomon.shared.security.config.SecurityConfig;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserUseCase;
import io.gnomon.tenancy.application.port.in.result.LocalUserResult;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = "gnomon.cors.allowed-origins=http://localhost:3000")
@WebAppConfiguration
class SecurityConfigTest {

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;
  private final Logger securityLogger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
  private final Logger accessLogger =
      (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    securityLogger.addAppender(appender);
    accessLogger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    securityLogger.detachAppender(appender);
    accessLogger.detachAppender(appender);
    appender.stop();
    MDC.clear();
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
  void corsPreflight_publicGet_shouldBeAllowed() throws Exception {
    mockMvc
        .perform(
            options("/v1/public/tenants/umbra-smoke")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void corsPreflight_publicBooking_shouldAllowContentTypeAndIdempotencyKey() throws Exception {
    mockMvc
        .perform(
            options("/v1/public/tenants/umbra-smoke/appointments")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type, Idempotency-Key"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
        .andExpect(
            header()
                .string(
                    "Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
  }

  @Test
  void corsPreflight_authenticatedRequest_shouldAllowAuthorization() throws Exception {
    mockMvc
        .perform(
            options("/v1/tenants")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
  }

  @Test
  void corsPreflight_unauthorizedOrigin_shouldBeRejected() throws Exception {
    mockMvc
        .perform(
            options("/v1/public/tenants/umbra-smoke")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
  }

  @Test
  void corsPreflight_adminPut_shouldBeRejectedWithoutPermissiveHeaders() throws Exception {
    mockMvc
        .perform(
            options(
                    "/v1/tenants/umbra-smoke/calendars/30000000-0000-4000-8000-000000000001/offerings")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
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
    assertThat(configuration.getAllowedMethods())
        .containsExactly("GET", "POST", "PATCH", "DELETE", "OPTIONS")
        .doesNotContain("PUT");
    assertThat(configuration.getAllowedHeaders())
        .contains("Authorization", "Content-Type", "Idempotency-Key");
  }

  @Test
  void accessDeniedHandler_shouldLogSafeEventAndReturnStableErrorEnvelope() throws Exception {
    var response = new org.springframework.mock.web.MockHttpServletResponse();
    var request =
        new org.springframework.mock.web.MockHttpServletRequest("POST", "/v1/private/secret");
    request.addHeader("Authorization", "Bearer sensitive-jwt");

    new RequestCorrelationFilter()
        .doFilter(
            request,
            response,
            (filteredRequest, filteredResponse) ->
                new SecurityConfig()
                    .accessDeniedHandler()
                    .handle(
                        (jakarta.servlet.http.HttpServletRequest) filteredRequest,
                        (HttpServletResponse) filteredResponse,
                        new org.springframework.security.access.AccessDeniedException("denied")));

    assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":{\"code\":\"forbidden\",\"message\":\"forbidden\",\"details\":null}}");
    assertThat(appender.list)
        .filteredOn(event -> event.getKeyValuePairs() != null)
        .extracting(SecurityConfigTest::eventName)
        .containsExactlyInAnyOrder("auth.access_denied", "http.request.completed");
    assertThat(appender.list)
        .filteredOn(event -> "auth.access_denied".equals(eventName(event)))
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getMDCPropertyMap()).containsKeys("request.id", "correlation.id");
              assertThat(event.getFormattedMessage()).doesNotContain("secret", "sensitive-jwt");
              assertThat(event.getKeyValuePairs().toString())
                  .doesNotContain("secret", "sensitive-jwt");
            });
  }

  private static String eventName(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .filter(pair -> "event_name".equals(pair.key))
        .map(pair -> String.valueOf(pair.value))
        .findFirst()
        .orElseThrow();
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
