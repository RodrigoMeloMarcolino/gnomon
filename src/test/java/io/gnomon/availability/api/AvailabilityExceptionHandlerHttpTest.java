package io.gnomon.availability.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.availability.api.exception.AvailabilityExceptionHandler;
import io.gnomon.availability.domain.exception.AvailabilityException;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class AvailabilityExceptionHandlerHttpTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailureProbeController())
            .setControllerAdvice(new AvailabilityExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @Test
  void crossTenantAccess_shouldReturn403() throws Exception {
    mockMvc
        .perform(get("/test/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("availability_access_denied"));
  }

  @Test
  void missingRule_shouldReturn404() throws Exception {
    mockMvc
        .perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("availability_rule_not_found"));
  }

  @Test
  void knownConstraint_shouldReturn422() throws Exception {
    mockMvc
        .perform(get("/test/integrity"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @RestController
  @RequestMapping("/test")
  static class FailureProbeController {

    @GetMapping("/denied")
    String denied() {
      throw new AvailabilityException(
          "availability_access_denied", "cross-tenant access is forbidden");
    }

    @GetMapping("/not-found")
    String notFound() {
      throw new AvailabilityException(
          "availability_rule_not_found", "availability rule was not found");
    }

    @GetMapping("/integrity")
    String integrity() {
      throw new DataIntegrityViolationException(
          "constraint failed", new IllegalStateException("ck_availability_rules_time_alignment"));
    }
  }
}
