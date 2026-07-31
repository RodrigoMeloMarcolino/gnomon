package io.gnomon.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/** Creates and clears request correlation context at the outer HTTP boundary. */
public final class RequestCorrelationFilter extends OncePerRequestFilter {

  private static final Pattern CORRELATION_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final StructuredEventLogger LOGGER =
      StructuredEventLogger.getLogger(RequestCorrelationFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    String correlationId = correlationId(request, requestId);
    Instant startedAt = Instant.now();
    StatusCapturingResponse wrapped = new StatusCapturingResponse(response);
    response.setHeader("X-Request-ID", requestId);
    response.setHeader("X-Correlation-ID", correlationId);
    MDC.put("request.id", requestId);
    MDC.put("correlation.id", correlationId);
    try {
      filterChain.doFilter(request, wrapped);
      logTerminal(request, wrapped.status(), startedAt, null);
    } catch (Exception exception) {
      if (!wrapped.isCommitted()) {
        writeUnexpectedError(wrapped);
      }
      logTerminal(request, wrapped.status(), startedAt, exception);
    } finally {
      MDC.remove("request.id");
      MDC.remove("correlation.id");
    }
  }

  private static String correlationId(HttpServletRequest request, String requestId) {
    String correlation = request.getHeader("X-Correlation-ID");
    if (valid(correlation)) {
      return correlation;
    }
    String requested = request.getHeader("X-Request-ID");
    return valid(requested) ? requested : requestId;
  }

  private static boolean valid(String value) {
    return value != null && CORRELATION_ID.matcher(value).matches();
  }

  private static void logTerminal(
      HttpServletRequest request, int status, Instant startedAt, Exception exception) {
    String route = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    Map<String, Object> attributes =
        Map.of(
            "http.method",
            request.getMethod(),
            "http.route",
            route == null ? "unmatched" : route,
            "http.status_code",
            status,
            "duration_ms",
            Duration.between(startedAt, Instant.now()).toMillis());
    if (exception == null && status < 500) {
      LOGGER.info("http.request.completed", "HTTP request completed", attributes);
    } else {
      LOGGER.error("http.request.failed", "HTTP request failed", attributes, exception);
    }
  }

  private static void writeUnexpectedError(StatusCapturingResponse response) throws IOException {
    response.resetBuffer();
    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"error\":{\"code\":\"internal_server_error\",\"message\":\"an unexpected error occurred\",\"details\":null}}");
  }

  private static final class StatusCapturingResponse extends HttpServletResponseWrapper {
    private int status = HttpServletResponse.SC_OK;

    private StatusCapturingResponse(HttpServletResponse response) {
      super(response);
    }

    @Override
    public void setStatus(int status) {
      this.status = status;
      super.setStatus(status);
    }

    @Override
    public void sendError(int status) throws IOException {
      this.status = status;
      super.sendError(status);
    }

    @Override
    public void sendError(int status, String message) throws IOException {
      this.status = status;
      super.sendError(status, message);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
      this.status = HttpServletResponse.SC_FOUND;
      super.sendRedirect(location);
    }

    private int status() {
      return status;
    }
  }
}
