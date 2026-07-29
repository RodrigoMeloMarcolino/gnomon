package io.gnomon.shared.security.authentication;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Authenticated request identity combining the local projection with its source JWT. */
public final class LocalUserAuthenticationToken extends AbstractAuthenticationToken {

  private final LocalUserPrincipal principal;
  private final Jwt jwt;

  public LocalUserAuthenticationToken(
      LocalUserPrincipal principal,
      Jwt jwt,
      Collection<? extends GrantedAuthority> authorities,
      Object details) {
    super(authorities);
    this.principal = principal;
    this.jwt = jwt;
    setDetails(details);
    setAuthenticated(true);
  }

  @Override
  public LocalUserPrincipal getPrincipal() {
    return principal;
  }

  @Override
  public Jwt getCredentials() {
    return jwt;
  }

  public Jwt jwt() {
    return jwt;
  }
}
