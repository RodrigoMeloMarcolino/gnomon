package io.gnomon.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@Tag("integration")
@SpringJUnitConfig
@ContextConfiguration(
    classes = {SecurityConfig.class, KeycloakResourceServerIntegrationTest.TestWebConfig.class})
@WebAppConfiguration
class KeycloakResourceServerIntegrationTest {

  private static final int KEYCLOAK_PORT = 8080;
  private static final String REALM = "gnomon-test";
  private static final String REQUIRED_AUDIENCE = "gnomon-api";
  private static final Pattern ACCESS_TOKEN_PATTERN =
      Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

  private static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.7.0"))
          .withExposedPorts(KEYCLOAK_PORT)
          .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
          .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("keycloak/realm-gnomon-test.json"),
              "/opt/keycloak/data/import/realm-gnomon-test.json")
          .withCommand("start-dev", "--http-port=8080", "--import-realm")
          .waitingFor(
              Wait.forHttp("/realms/gnomon-test/.well-known/openid-configuration")
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofMinutes(2)));

  static {
    KEYCLOAK.start();
  }

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterAll
  static void stopKeycloak() {
    KEYCLOAK.stop();
  }

  @Test
  void protectedRoute_withRealValidToken_shouldBeAccepted() throws Exception {
    var token = requestToken("gnomon-test-valid");

    mockMvc
        .perform(get("/v1/test/protected").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void protectedRoute_withRealTokenWithoutRequiredAudience_shouldBeRejected() throws Exception {
    var token = requestToken("gnomon-test-invalid-audience");

    mockMvc
        .perform(get("/v1/test/protected").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("unauthorized"));
  }

  @Test
  void jwtDecoder_withDifferentExpectedIssuer_shouldRejectOtherwiseValidToken() {
    var token = requestToken("gnomon-test-valid");
    var decoder = jwtDecoder("https://wrong-issuer.example/realms/gnomon-test");

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
  }

  private static String requestToken(String clientId) {
    try {
      var form =
          "grant_type=password"
              + "&client_id="
              + encode(clientId)
              + "&username=security-test"
              + "&password=security-test-password";
      var request =
          HttpRequest.newBuilder()
              .uri(URI.create(issuerUri() + "/protocol/openid-connect/token"))
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(HttpRequest.BodyPublishers.ofString(form))
              .build();
      var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

      assertThat(response.statusCode()).isEqualTo(200);
      var matcher = ACCESS_TOKEN_PATTERN.matcher(response.body());
      assertThat(matcher.find()).as("Keycloak access_token response").isTrue();
      return matcher.group(1);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while obtaining a Keycloak token", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Could not obtain a Keycloak token", exception);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static String issuerUri() {
    return "http://"
        + KEYCLOAK.getHost()
        + ":"
        + KEYCLOAK.getMappedPort(KEYCLOAK_PORT)
        + "/realms/"
        + REALM;
  }

  private static String jwkSetUri() {
    return issuerUri() + "/protocol/openid-connect/certs";
  }

  private static JwtDecoder jwtDecoder(String expectedIssuer) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri()).build();
    var issuerValidator = JwtValidators.createDefaultWithIssuer(expectedIssuer);
    var audienceValidator =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            audiences -> audiences != null && audiences.contains(REQUIRED_AUDIENCE));
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<Jwt>(issuerValidator, audienceValidator));
    return decoder;
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
      return KeycloakResourceServerIntegrationTest.jwtDecoder(issuerUri());
    }
  }

  @RestController
  static class TestController {

    @GetMapping("/v1/test/protected")
    String protectedRoute() {
      return "ok";
    }
  }
}
