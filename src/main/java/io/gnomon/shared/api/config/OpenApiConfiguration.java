package io.gnomon.shared.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
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
                        .bearerFormat("JWT")));
  }

  @Bean
  OpenApiCustomizer standardErrorResponses() {
    return openApi ->
        openApi
            .getPaths()
            .values()
            .forEach(
                path ->
                    path.readOperations()
                        .forEach(
                            operation -> {
                              ApiResponses responses = operation.getResponses();
                              if (responses != null) {
                                responses.addApiResponse(
                                    "422", errorResponse("Request validation failed"));
                              }
                            }));
  }

  private static ApiResponse errorResponse(String description) {
    Schema<?> fieldError =
        new ObjectSchema()
            .addProperty("field", new Schema<>().type("string"))
            .addProperty("message", new Schema<>().type("string"));
    Schema<?> error =
        new ObjectSchema()
            .addProperty("code", new Schema<>().type("string"))
            .addProperty("message", new Schema<>().type("string"))
            .addProperty("details", new ArraySchema().items(fieldError).nullable(true));
    return new ApiResponse()
        .description(description)
        .content(
            new io.swagger.v3.oas.models.media.Content()
                .addMediaType(
                    "application/json",
                    new io.swagger.v3.oas.models.media.MediaType()
                        .schema(new ObjectSchema().addProperty("error", error))));
  }
}
