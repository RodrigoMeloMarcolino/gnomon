package io.gnomon.booking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.booking.application.CreateAppointmentService;
import io.gnomon.booking.domain.BookingException;
import io.gnomon.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class BookingExceptionHandlerHttpTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new FailureProbeController())
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @Test
  void advisorySlotRejection_shouldExposeSlotUnavailableAs422() throws Exception {
    mockMvc
        .perform(get("/test/advisory"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("slot_unavailable"));
  }

  @Test
  void databaseSlotConflict_shouldExposeSlotUnavailableAs409() throws Exception {
    mockMvc
        .perform(get("/test/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("slot_unavailable"));
  }

  @Test
  void knownCheckConstraint_shouldReturnCanonical422() throws Exception {
    mockMvc
        .perform(get("/test/check"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @RestController
  @RequestMapping("/test")
  static class FailureProbeController {

    @GetMapping("/advisory")
    String advisory() {
      throw new BookingException(
          CreateAppointmentService.SLOT_UNAVAILABLE_VALIDATION,
          "requested start time is not available");
    }

    @GetMapping("/conflict")
    String conflict() {
      throw new BookingException("slot_unavailable", "requested slot is unavailable");
    }

    @GetMapping("/check")
    String check() {
      throw new DataIntegrityViolationException(
          "constraint failed", new IllegalStateException("ck_appointments_time_order"));
    }
  }
}
