package io.gnomon.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.gnomon.booking.api.controller.PublicAppointmentController;
import io.gnomon.booking.api.exception.BookingExceptionHandler;
import io.gnomon.booking.application.port.in.AppointmentResult;
import io.gnomon.booking.application.port.in.CalendarSummary;
import io.gnomon.booking.application.port.in.CreateAppointmentCommand;
import io.gnomon.booking.application.port.in.CreateAppointmentUseCase;
import io.gnomon.booking.application.port.in.CreationResult;
import io.gnomon.booking.application.port.in.CustomerSummary;
import io.gnomon.booking.application.port.in.OfferingSummary;
import io.gnomon.shared.api.exception.GlobalExceptionHandler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PublicAppointmentControllerHttpTest {

  private static final String IDEMPOTENCY_KEY = "10000000-0000-4000-8000-000000000001";

  private static final UUID TENANT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID CALENDAR_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
  private static final UUID OFFERING_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
  private static final UUID CUSTOMER_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID APPOINTMENT_ID =
      UUID.fromString("60000000-0000-0000-0000-000000000001");

  @Mock private CreateAppointmentUseCase createAppointment;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PublicAppointmentController(createAppointment))
            .setControllerAdvice(new BookingExceptionHandler(), new GlobalExceptionHandler())
            .build();
  }

  @Test
  void create_whenAppointmentIsNew_shouldReturn201AndExactPublicContract() throws Exception {
    when(createAppointment.create(any())).thenReturn(new CreationResult(result(), false));

    mockMvc
        .perform(
            post("/v1/public/tenants/barbearia-solar/appointments")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isCreated())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "id":"60000000-0000-0000-0000-000000000001",
                      "start_at":"2027-07-01T12:00:00Z",
                      "end_at":"2027-07-01T12:30:00Z",
                      "status":"scheduled",
                      "calendar":{
                        "id":"30000000-0000-0000-0000-000000000001",
                        "name":"Agenda da Joana",
                        "timezone":"America/Fortaleza"
                      },
                      "offering":{
                        "id":"40000000-0000-0000-0000-000000000001",
                        "title":"Corte",
                        "duration_minutes":30,
                        "price_cents":4500
                      },
                      "customer":{
                        "id":"50000000-0000-0000-0000-000000000001",
                        "name":"Ana",
                        "phone":"+5585999999999",
                        "email":"ana@example.com"
                      },
                      "customer_notes":"Janela"
                    }
                    """,
                    true))
        .andExpect(jsonPath("$.success").doesNotExist());

    var command = ArgumentCaptor.forClass(CreateAppointmentCommand.class);
    verify(createAppointment).create(command.capture());
    assertThat(command.getValue().startAt()).isEqualTo(Instant.parse("2027-07-01T12:00:00Z"));
  }

  @Test
  void create_whenAppointmentIsReplay_shouldReturn200() throws Exception {
    when(createAppointment.create(any())).thenReturn(new CreationResult(result(), true));

    mockMvc
        .perform(
            post("/v1/public/tenants/barbearia-solar/appointments")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(APPOINTMENT_ID.toString()));
  }

  @Test
  void create_whenIdempotencyKeyIsMissing_shouldReturnCanonical422() throws Exception {
    mockMvc
        .perform(
            post("/v1/public/tenants/barbearia-solar/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"))
        .andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
  }

  @Test
  void create_whenStartAtHasNoOffset_shouldReturnCanonical422() throws Exception {
    mockMvc
        .perform(
            post("/v1/public/tenants/barbearia-solar/appointments")
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody().replace("2027-07-01T09:00:00-03:00", "2027-07-01T09:00:00")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  @Test
  void create_whenIdempotencyKeyIsNotCanonicalUuid_shouldReturnCanonical422() throws Exception {
    mockMvc
        .perform(
            post("/v1/public/tenants/barbearia-solar/appointments")
                .header("Idempotency-Key", "intent-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error.code").value("validation_error"));
  }

  private static String validBody() {
    return """
    {
      "calendar_id":"30000000-0000-0000-0000-000000000001",
      "offering_id":"40000000-0000-0000-0000-000000000001",
      "start_at":"2027-07-01T09:00:00-03:00",
      "customer_name":"Ana",
      "customer_phone":"(85) 99999-9999",
      "customer_email":"ana@example.com",
      "customer_notes":"Janela"
    }
    """;
  }

  private static AppointmentResult result() {
    return new AppointmentResult(
        APPOINTMENT_ID,
        Instant.parse("2027-07-01T12:00:00Z"),
        Instant.parse("2027-07-01T12:30:00Z"),
        "scheduled",
        new CalendarSummary(CALENDAR_ID, "Agenda da Joana", "America/Fortaleza"),
        new OfferingSummary(OFFERING_ID, "Corte", 30, 4_500),
        new CustomerSummary(CUSTOMER_ID, "Ana", "+5585999999999", "ana@example.com"),
        "Janela");
  }
}
