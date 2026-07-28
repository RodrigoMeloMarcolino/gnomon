package io.gnomon.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {

  @Test
  void healthReturnsOkWithoutTouchingInfrastructure() {
    var controller = new HealthController(new ReadyJdbcTemplate(1));

    assertThat(controller.health().status()).isEqualTo("ok");
  }

  @Test
  void readyReturnsReadyWhenDatabaseIsReachable() {
    var controller = new HealthController(new ReadyJdbcTemplate(1));

    var response = controller.ready();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("ready");
  }

  @Test
  void readyReturnsUnavailableWhenDatabaseIsDown() {
    var controller =
        new HealthController(
            new FailingJdbcTemplate(new DataAccessResourceFailureException("connection refused")));

    var response = controller.ready();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo("unavailable");
  }

  private static final class ReadyJdbcTemplate extends JdbcTemplate {
    private final Integer result;

    private ReadyJdbcTemplate(Integer result) {
      this.result = result;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
      return requiredType.cast(result);
    }
  }

  private static final class FailingJdbcTemplate extends JdbcTemplate {
    private final DataAccessException error;

    private FailingJdbcTemplate(DataAccessException error) {
      this.error = error;
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
      throw error;
    }
  }
}
