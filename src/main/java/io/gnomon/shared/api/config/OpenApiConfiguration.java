package io.gnomon.shared.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Generated integration contract for Umbra; controllers remain the executable source of paths. */
@Configuration
public class OpenApiConfiguration {

  @Bean
  OpenAPI gnomonOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Gnomon API")
                .version("v1")
                .description("Gnomon → Umbra integration contract"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSchemas("ApiErrorResponse", errorEnvelopeSchema()));
  }

  @Bean
  OpenApiCustomizer standardErrorResponses() {
    return openApi -> {
      openApi
          .getPaths()
          .entrySet()
          .forEach(
              entry ->
                  entry
                      .getValue()
                      .readOperationsMap()
                      .forEach(
                          (method, operation) ->
                              customizeOperation(entry.getKey(), method.name(), operation)));
      renamePublicSchemas(openApi);
    };
  }

  private static void customizeOperation(String path, String method, Operation operation) {
    boolean publicOperation = path.startsWith("/v1/public/");
    boolean infrastructureOperation =
        path.equals("/v1/health") || path.equals("/v1/ready") || path.startsWith("/v3/");
    if (!publicOperation && !infrastructureOperation) {
      operation.setSecurity(List.of(new SecurityRequirement().addList("bearerAuth")));
      addResponse(operation, "401", errorResponse("Authentication required"));
      addResponse(operation, "403", errorResponse("Access denied"));
    } else {
      operation.setSecurity(null);
    }

    addResponse(operation, "422", errorResponse("Request validation failed"));
    boolean publicBookingPost =
        method.equals("POST") && path.startsWith("/v1/public/") && path.endsWith("/appointments");
    if (publicBookingPost) {
      addResponse(operation, "409", errorResponse("Booking conflicts with an existing request"));
      addResponse(
          operation,
          "201",
          new ApiResponse()
              .description("Created")
              .content(
                  new Content()
                      .addMediaType(
                          "application/json",
                          new MediaType()
                              .schema(
                                  new Schema<>()
                                      .$ref("#/components/schemas/AppointmentResponse")))));
      Parameter key =
          operation.getParameters().stream()
              .filter(parameter -> "Idempotency-Key".equalsIgnoreCase(parameter.getName()))
              .findFirst()
              .orElse(null);
      if (key != null) {
        key.setDescription("Canonical lowercase UUID used to make booking retries safe.");
        key.setExample("90000000-0000-4000-8000-000000000001");
      }
    }
    if (operation.getParameters() != null) {
      operation.getParameters().forEach(OpenApiConfiguration::addUmbraExample);
    }
    if (publicBookingPost && operation.getRequestBody() != null) {
      MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
      if (mediaType != null) {
        mediaType.setExample(
            Map.of(
                "calendar_id", "30000000-0000-4000-8000-000000000001",
                "offering_id", "40000000-0000-4000-8000-000000000001",
                "start_at", "2026-08-02T12:00:00-03:00",
                "customer_name", "Umbra Smoke",
                "customer_phone", "+5585999990000"));
      }
    }
  }

  private static void addUmbraExample(Parameter parameter) {
    switch (parameter.getName()) {
      case "tenantSlug" -> parameter.setExample("umbra-smoke");
      case "calendar_id" -> parameter.setExample("30000000-0000-4000-8000-000000000001");
      case "offering_id" -> parameter.setExample("40000000-0000-4000-8000-000000000001");
      case "date" -> parameter.setExample("2026-08-02");
      default -> {}
    }
  }

  private static void addResponse(Operation operation, String status, ApiResponse response) {
    ApiResponses responses = operation.getResponses();
    if (responses != null && !responses.containsKey(status)) {
      responses.addApiResponse(status, response);
    }
  }

  private static ApiResponse errorResponse(String description) {
    return new ApiResponse()
        .description(description)
        .content(
            new Content()
                .addMediaType(
                    "application/json",
                    new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))));
  }

  private static Schema errorEnvelopeSchema() {
    Schema<?> fieldError =
        new ObjectSchema()
            .addProperty("field", new Schema<>().type("string"))
            .addProperty("message", new Schema<>().type("string"));
    Schema<?> error =
        new ObjectSchema()
            .addProperty("code", new Schema<>().type("string"))
            .addProperty("message", new Schema<>().type("string"))
            .addProperty("details", new ArraySchema().items(fieldError).nullable(true));
    return new ObjectSchema().addProperty("error", error);
  }

  private static void renamePublicSchemas(OpenAPI openApi) {
    Map<String, Schema> schemas = openApi.getComponents().getSchemas();
    schemas.put("ApiErrorResponse", errorEnvelopeSchema());
    List.of(
            "AvailableSlotsResponse",
            "CreateAppointmentRequest",
            "PublicCalendarResponse",
            "PublicOfferingResponse",
            "PublicTenantProfileResponse",
            "TenantSelectionResponse")
        .forEach(name -> schemas.put(name, snakeCopy(schemas.get(name))));

    schemas.put("BookingCalendarResponse", snakeCopy(schemas.get("CalendarResponse")));
    schemas.put("BookingOfferingResponse", snakeCopy(schemas.get("OfferingResponse")));
    schemas.put("BookingCustomerResponse", snakeCopy(schemas.get("CustomerResponse")));
    Schema appointment = snakeCopy(schemas.get("AppointmentResponse"));
    ((Schema) appointment.getProperties().get("calendar"))
        .set$ref("#/components/schemas/BookingCalendarResponse");
    ((Schema) appointment.getProperties().get("offering"))
        .set$ref("#/components/schemas/BookingOfferingResponse");
    ((Schema) appointment.getProperties().get("customer"))
        .set$ref("#/components/schemas/BookingCustomerResponse");
    schemas.put("AppointmentResponse", appointment);
  }

  private static Schema snakeCopy(Schema source) {
    if (source == null || source.getProperties() == null) {
      return source;
    }
    Schema copy = new Schema<>().type(source.getType());
    Map<String, Schema> properties = new LinkedHashMap<>();
    source
        .getProperties()
        .forEach(
            (property, schema) ->
                properties.put(toSnakeCase(property.toString()), (Schema) schema));
    copy.setProperties(properties);
    if (source.getRequired() != null) {
      copy.setRequired(
          source.getRequired().stream()
              .map(Object::toString)
              .map(value -> toSnakeCase(value.toString()))
              .toList());
    }
    return copy;
  }

  private static String toSnakeCase(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
  }
}
