package io.gnomon.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.catalog.api.exception.CatalogExceptionHandler;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class CatalogExceptionHandlerHttpTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailureProbeController())
            .setControllerAdvice(new CatalogExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @Test
  void catalogAccessDenied_shouldReturn403() throws Exception {
    mockMvc
        .perform(get("/test/denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("staff_calendar_mismatch"));
  }

  @Test
  void offeringNotFound_shouldReturn404() throws Exception {
    mockMvc
        .perform(get("/test/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("offering_not_found"));
  }

  @Test
  void calendarExists_shouldReturn409() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("calendar_exists"));
  }

  @Test
  void knownDataIntegrityViolation_shouldReturn422() throws Exception {
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
      throw new CatalogException(
          "staff_calendar_mismatch", "staff can only access their own calendar");
    }

    @GetMapping("/not-found")
    String notFound() {
      throw new CatalogException("offering_not_found", "offering was not found");
    }

    @GetMapping("/conflict")
    String conflict() {
      throw new CatalogException("calendar_exists", "collaborator already has a calendar");
    }

    @GetMapping("/integrity")
    String integrity() {
      throw new DataIntegrityViolationException(
          "constraint failed", new IllegalStateException("ck_offerings_duration"));
    }
  }
}
