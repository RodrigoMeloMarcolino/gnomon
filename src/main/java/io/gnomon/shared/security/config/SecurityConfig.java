package io.gnomon.shared.security.config;

import io.gnomon.shared.logging.RequestCorrelationFilter;
import io.gnomon.shared.security.filter.LocalUserProvisioningFilter;
import io.gnomon.tenancy.application.port.in.ProvisionLocalUserUseCase;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/** OAuth2 resource server stateless conforme a spec de autenticação via Keycloak. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint authenticationEntryPoint,
      AccessDeniedHandler accessDeniedHandler,
      LocalUserProvisioningFilter localUserProvisioningFilter,
      RequestCorrelationFilter requestCorrelationFilter)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/v1/public/**",
                        "/v1/health",
                        "/v1/ready",
                        "/v3/api-docs",
                        "/v3/api-docs.yaml",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
                    .jwt(Customizer.withDefaults()))
        .addFilterBefore(requestCorrelationFilter, SecurityContextHolderFilter.class)
        .addFilterAfter(localUserProvisioningFilter, BearerTokenAuthenticationFilter.class)
        .build();
  }

  @Bean
  LocalUserProvisioningFilter localUserProvisioningFilter(
      ProvisionLocalUserUseCase provisionLocalUser,
      ObjectMapper objectMapper,
      @Value("${gnomon.security.require-verified-email:false}") boolean requireVerifiedEmail) {
    return new LocalUserProvisioningFilter(provisionLocalUser, objectMapper, requireVerifiedEmail);
  }

  @Bean
  RequestCorrelationFilter requestCorrelationFilter() {
    return new RequestCorrelationFilter();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${gnomon.cors.allowed-origins:}") String allowedOrigins) {
    var configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(parseOrigins(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            "Idempotency-Key",
            "X-Request-ID",
            "X-Correlation-ID"));
    configuration.setExposedHeaders(List.of("X-Request-ID", "X-Correlation-ID"));
    configuration.setMaxAge(3600L);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/v1/**", configuration);
    return source;
  }

  @Bean
  AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, exception) ->
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized", "unauthorized");
  }

  @Bean
  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, exception) ->
        writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden", "forbidden");
  }

  private static List<String> parseOrigins(String origins) {
    if (origins == null || origins.isBlank()) {
      return List.of();
    }
    return Arrays.stream(origins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .distinct()
        .toList();
  }

  private static void writeError(
      HttpServletResponse response, int status, String code, String message) throws IOException {
    response.setStatus(status);
    if (status == HttpServletResponse.SC_UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    }
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            """
            {"error":{"code":"%s","message":"%s","details":null}}\
            """
                .formatted(code, message));
  }
}
