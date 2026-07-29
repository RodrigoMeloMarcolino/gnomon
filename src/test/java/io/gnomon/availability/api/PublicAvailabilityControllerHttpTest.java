package io.gnomon.availability.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.availability.api.controller.PublicAvailabilityController;
import io.gnomon.availability.api.exception.AvailabilityExceptionHandler;
import io.gnomon.availability.application.port.in.ListAvailableSlotsUseCase;
import io.gnomon.catalog.api.exception.CatalogExceptionHandler;
import io.gnomon.catalog.domain.exception.CatalogException;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicAvailabilityControllerHttpTest {

  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final LocalDate DATE = LocalDate.of(2027, 7, 1);

  @Mock private ListAvailableSlotsUseCase listAvailableSlots;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PublicAvailabilityController(listAvailableSlots))
            .setControllerAdvice(
                new AvailabilityExceptionHandler(),
                new CatalogExceptionHandler(),
                new GlobalExceptionHandler())
            .build();
  }

  @Test
  void list_whenRequestIsValid_shouldReturnExactUtcContract() throws Exception {
    when(listAvailableSlots.list("barbearia-solar", CALENDAR_ID, OFFERING_ID, DATE))
        .thenReturn(
            List.of(Instant.parse("2027-07-01T12:00:00Z"), Instant.parse("2027-07-01T12:30:00Z")));

    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/available-slots")
                .queryParam("calendar_id", CALENDAR_ID.toString())
                .queryParam("offering_id", OFFERING_ID.toString())
                .queryParam("date", DATE.toString()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {"available_start_times":[
                      "2027-07-01T12:00:00Z",
                      "2027-07-01T12:30:00Z"
                    ]}
                    """,
                    true))
        .andExpect(jsonPath("$.success").doesNotExist());

    verify(listAvailableSlots).list("barbearia-solar", CALENDAR_ID, OFFERING_ID, DATE);
  }

  @Test
  void list_whenOfferingIsNotSchedulable_shouldReturnCanonical404() throws Exception {
    when(listAvailableSlots.list("barbearia-solar", CALENDAR_ID, OFFERING_ID, DATE))
        .thenThrow(new CatalogException("offering_not_found", "offering was not found"));

    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/available-slots")
                .queryParam("calendar_id", CALENDAR_ID.toString())
                .queryParam("offering_id", OFFERING_ID.toString())
                .queryParam("date", DATE.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("offering_not_found"))
        .andExpect(jsonPath("$.error.message").value("offering was not found"))
        .andExpect(jsonPath("$.error.details").isEmpty());
  }

  @Test
  void list_whenDateIsInvalid_shouldReturnValidationEnvelope() throws Exception {
    mockMvc
        .perform(
            get("/v1/public/tenants/barbearia-solar/available-slots")
                .queryParam("calendar_id", CALENDAR_ID.toString())
                .queryParam("offering_id", OFFERING_ID.toString())
                .queryParam("date", "01-07-2027"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }
}
