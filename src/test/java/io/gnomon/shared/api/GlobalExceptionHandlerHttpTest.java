package io.gnomon.shared.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerHttpTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailureProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void accessDenied_whenRaisedDuringMvcDispatch_shouldReturn403Envelope() throws Exception {
    mockMvc
        .perform(get("/test/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("forbidden"))
        .andExpect(jsonPath("$.error.message").value("access is denied"));
  }

  @Test
  void authorizationDenied_whenRaisedDuringMvcDispatch_shouldReturn403Envelope() throws Exception {
    mockMvc
        .perform(get("/test/authorization-denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("forbidden"));
  }

  @Test
  void noResourceFound_whenRaisedDuringMvcDispatch_shouldReturn404Envelope() throws Exception {
    mockMvc
        .perform(get("/test/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("not_found"))
        .andExpect(jsonPath("$.error.message").value("resource not found"));
  }

  @RestController
  @RequestMapping("/test")
  static class FailureProbeController {

    @GetMapping("/denied")
    String denied() {
      throw new AccessDeniedException("implementation detail");
    }

    @GetMapping("/authorization-denied")
    String authorizationDenied() {
      throw new AuthorizationDeniedException("implementation detail");
    }

    @GetMapping("/missing")
    String missing() throws NoResourceFoundException {
      throw new NoResourceFoundException(HttpMethod.GET, "/test/missing", "");
    }
  }
}
