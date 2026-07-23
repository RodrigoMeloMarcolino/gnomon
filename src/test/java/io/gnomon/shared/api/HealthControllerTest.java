package io.gnomon.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final HealthController controller = new HealthController(jdbcTemplate);

  @Test
  void healthReturnsOkWithoutTouchingInfrastructure() {
    assertThat(controller.health().status()).isEqualTo("ok");
  }

  @Test
  void readyReturnsReadyWhenDatabaseIsReachable() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

    var response = controller.ready();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("ready");
  }

  @Test
  void readyReturnsUnavailableWhenDatabaseIsDown() {
    when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    var response = controller.ready();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("unavailable");
  }
}
