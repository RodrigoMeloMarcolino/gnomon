package io.gnomon.shared.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RequestCorrelationFilterTest {

  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();
  private final Logger logger = (Logger) LoggerFactory.getLogger(RequestCorrelationFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() {
    appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
    MDC.clear();
  }

  @Test
  void doFilter_withValidCallerCorrelation_shouldPropagateHeadersAndEmitOneTerminalEvent()
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/public/probe");
    request.setAttribute(
        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/v1/public/tenants/{slug}");
    request.addHeader("X-Correlation-ID", "operation-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) ->
            ((HttpServletResponse) ignoredResponse).setStatus(HttpServletResponse.SC_NO_CONTENT));

    assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("operation-123");
    assertThat(response.getHeader("X-Request-ID")).matches("[0-9a-f-]{36}");
    assertThat(MDC.get("request.id")).isNull();
    assertThat(MDC.get("correlation.id")).isNull();
    assertThat(events()).hasSize(1);
    assertThat(eventName(events().getFirst())).isEqualTo("http.request.completed");
    assertThat(events().getFirst().getKeyValuePairs())
        .anySatisfy(
            pair -> {
              assertThat(pair.key).isEqualTo("http.status_code");
              assertThat(pair.value).isEqualTo(204);
            });
    assertThat(events().getFirst().getMDCPropertyMap().get("correlation.id"))
        .isEqualTo("operation-123");
  }

  @Test
  void doFilter_withInvalidCallerCorrelation_shouldGenerateFallbackAndReturnSafe500Once()
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/public/probe");
    request.addHeader("X-Correlation-ID", "contains spaces");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (ignoredRequest, ignoredResponse) -> {
          throw new ServletException("unexpected failure");
        });

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getContentAsString())
        .isEqualTo(
            "{\"error\":{\"code\":\"internal_server_error\",\"message\":\"an unexpected error occurred\",\"details\":null}}");
    assertThat(response.getHeader("X-Correlation-ID")).matches("[0-9a-f-]{36}");
    assertThat(events()).hasSize(1);
    assertThat(eventName(events().getFirst())).isEqualTo("http.request.failed");
    assertThat(events().getFirst().getThrowableProxy().getClassName())
        .isEqualTo(ServletException.class.getName());
  }

  private List<ILoggingEvent> events() {
    return appender.list.stream()
        .filter(event -> event.getKeyValuePairs() != null)
        .filter(event -> eventName(event).startsWith("http.request."))
        .toList();
  }

  private static String eventName(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .filter(pair -> "event_name".equals(pair.key))
        .map(pair -> String.valueOf(pair.value))
        .findFirst()
        .orElseThrow();
  }
}
