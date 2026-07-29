package io.gnomon.tenancy.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.gnomon.shared.security.authentication.LocalUserAuthenticationToken;
import io.gnomon.shared.security.filter.LocalUserProvisioningFilter;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserUseCase;
import io.gnomon.tenancy.application.port.in.result.LocalUserResult;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class LocalUserProvisioningFilterTest {

  @Mock private ProvisionLocalUserUseCase useCase;
  @Mock private FilterChain chain;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_whenAnonymous_shouldSkipDatabaseAndContinue() throws Exception {
    var filter = filter(true);

    filter.doFilter(request, response, chain);

    verify(useCase, never()).provision(any());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_whenJwtIsValid_shouldInstallLocalPrincipalAndPreserveJwt() throws Exception {
    Jwt jwt = jwt(true, true);
    var jwtAuthentication =
        new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority("SCOPE_api")));
    jwtAuthentication.setDetails("request-details");
    SecurityContextHolder.getContext().setAuthentication(jwtAuthentication);
    UUID userId = UUID.randomUUID();
    when(useCase.provision(any()))
        .thenReturn(new LocalUserResult(userId, "keycloak-sub", "user@example.com", "Local User"));

    filter(true).doFilter(request, response, chain);

    var authentication =
        (LocalUserAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    assertThat(authentication.getPrincipal().userId()).isEqualTo(userId);
    assertThat(authentication.jwt()).isSameAs(jwt);
    assertThat(authentication.getAuthorities())
        .extracting("authority")
        .containsExactly("SCOPE_api");
    assertThat(authentication.getDetails()).isEqualTo("request-details");
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_whenEmailIsNotVerifiedAndRequired_shouldReturnStable401() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(authenticated(jwt(false, true)));

    filter(true).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":\"email_not_verified\"");
    verify(useCase, never()).provision(any());
    verify(chain, never()).doFilter(request, response);
  }

  @Test
  void doFilter_whenEmailVerificationIsDisabled_shouldProvisionUnverifiedUser() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(authenticated(jwt(false, true)));
    when(useCase.provision(any()))
        .thenReturn(
            new LocalUserResult(
                UUID.randomUUID(), "keycloak-sub", "user@example.com", "Local User"));

    filter(false).doFilter(request, response, chain);

    verify(useCase).provision(any());
    verify(chain).doFilter(request, response);
  }

  @Test
  void doFilter_whenRequiredClaimIsMissing_shouldReturnUnauthorizedWithoutDatabase()
      throws Exception {
    SecurityContextHolder.getContext().setAuthentication(authenticated(jwt(true, false)));

    filter(true).doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("\"code\":\"unauthorized\"");
    verify(useCase, never()).provision(any());
  }

  private LocalUserProvisioningFilter filter(boolean requireVerifiedEmail) {
    return new LocalUserProvisioningFilter(useCase, new ObjectMapper(), requireVerifiedEmail);
  }

  private static JwtAuthenticationToken authenticated(Jwt jwt) {
    return new JwtAuthenticationToken(jwt, java.util.List.of());
  }

  private static Jwt jwt(boolean emailVerified, boolean includeName) {
    var builder =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("keycloak-sub")
            .claim("email", "user@example.com")
            .claim("email_verified", emailVerified)
            .issuedAt(Instant.parse("2026-07-28T12:00:00Z"))
            .expiresAt(Instant.parse("2026-07-28T12:10:00Z"));
    if (includeName) {
      builder.claim("name", "Local User");
    }
    return builder.build();
  }
}
