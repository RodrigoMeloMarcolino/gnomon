package io.gnomon.shared.security.filter;

import io.gnomon.shared.security.authentication.LocalUserAuthenticationToken;
import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserCommand;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserUseCase;
import io.gnomon.tenancy.domain.exception.TenancyException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Projects an authenticated Keycloak identity into the local database and replaces the request
 * principal. Register this filter after Spring Security's bearer-token authentication filter.
 */
public final class LocalUserProvisioningFilter extends OncePerRequestFilter {

  public static final String REQUIRE_VERIFIED_EMAIL_PROPERTY =
      "gnomon.security.require-verified-email";

  private final ProvisionLocalUserUseCase provisionLocalUser;
  private final ObjectMapper objectMapper;
  private final boolean requireVerifiedEmail;

  public LocalUserProvisioningFilter(
      ProvisionLocalUserUseCase provisionLocalUser,
      ObjectMapper objectMapper,
      boolean requireVerifiedEmail) {
    this.provisionLocalUser = provisionLocalUser;
    this.objectMapper = objectMapper;
    this.requireVerifiedEmail = requireVerifiedEmail;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
        || !authentication.isAuthenticated()) {
      filterChain.doFilter(request, response);
      return;
    }

    try {
      var jwt = jwtAuthentication.getToken();
      String subject = requiredClaim(jwt.getSubject(), "sub");
      String email = requiredClaim(jwt.getClaimAsString("email"), "email");
      String displayName = requiredClaim(jwt.getClaimAsString("name"), "name");
      if (requireVerifiedEmail && !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
        reject(response, "email_not_verified", "email must be verified");
        return;
      }

      LocalUserPrincipal principal =
          LocalUserPrincipal.from(
              provisionLocalUser.provision(
                  new ProvisionLocalUserCommand(subject, email, displayName)));
      var localAuthentication =
          new LocalUserAuthenticationToken(
              principal, jwt, jwtAuthentication.getAuthorities(), jwtAuthentication.getDetails());
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(localAuthentication);
      SecurityContextHolder.setContext(context);
      filterChain.doFilter(request, response);
    } catch (InvalidClaimException exception) {
      reject(response, "unauthorized", "required token claims are missing");
    } catch (TenancyException exception) {
      reject(response, "unauthorized", "local identity could not be resolved");
    }
  }

  private static String requiredClaim(String value, String claim) {
    if (value == null || value.isBlank()) {
      throw new InvalidClaimException(claim);
    }
    return value;
  }

  private void reject(HttpServletResponse response, String code, String message)
      throws IOException {
    SecurityContextHolder.clearContext();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(
        response.getOutputStream(), Map.of("error", new ErrorBody(code, message, null)));
  }

  private record ErrorBody(String code, String message, Object details) {}

  private static final class InvalidClaimException extends RuntimeException {

    private InvalidClaimException(String claim) {
      super(claim);
    }
  }
}
