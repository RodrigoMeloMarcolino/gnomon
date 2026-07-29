package io.gnomon.availability.api;

import io.gnomon.shared.security.authentication.LocalUserPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

final class LocalUserPrincipalArgumentResolver implements HandlerMethodArgumentResolver {

  private final LocalUserPrincipal principal;

  LocalUserPrincipalArgumentResolver(LocalUserPrincipal principal) {
    this.principal = principal;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
        && parameter.getParameterType() == LocalUserPrincipal.class;
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
    return principal;
  }
}
