package io.gnomon.tenancy.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.gnomon.tenancy.domain.TenancyException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class TenancyExceptionHandlerTest {

  private final TenancyExceptionHandler handler = new TenancyExceptionHandler();

  @Test
  void handle_whenCodeIsKnown_shouldMapStableStatusAndEnvelope() {
    var response =
        handler.handle(new TenancyException("tenant_slug_taken", "tenant slug is already in use"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("tenant_slug_taken");
    assertThat(response.getBody().error().message()).isEqualTo("tenant slug is already in use");
  }

  @Test
  void handle_whenCodeIsUnknown_shouldNotExposeDomainMessage() {
    var response = handler.handle(new TenancyException("new_unmapped_code", "sensitive detail"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().error().code()).isEqualTo("internal_server_error");
    assertThat(response.getBody().error().message()).isEqualTo("an unexpected error occurred");
  }
}
