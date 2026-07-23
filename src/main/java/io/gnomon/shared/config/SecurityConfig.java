package io.gnomon.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração temporária da fase 00: sem autenticação real, libera todas as rotas para permitir
 * health checks e desenvolvimento da fundação.
 *
 * <p>TODO(fase 01): substituir pela chain da spec {@code docs/specs/keycloak-auth.md} — permitAll
 * em {@code /v1/public/**}, {@code /v1/health}, {@code /v1/ready} e erro; JWT obrigatório em
 * qualquer outra rota.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .build();
  }
}
