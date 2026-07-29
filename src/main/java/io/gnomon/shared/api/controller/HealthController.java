package io.gnomon.shared.api.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de infraestrutura (ADR 0014 §5): fora do escopo de autenticação e do envelope de erro
 * de domínio.
 */
@RestController
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  public HealthController(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Liveness: apenas confirma que a aplicação está viva. */
  @GetMapping("/v1/health")
  public StatusResponse health() {
    return new StatusResponse("ok");
  }

  /** Readiness: valida também a conexão com o PostgreSQL. */
  @GetMapping("/v1/ready")
  public ResponseEntity<StatusResponse> ready() {
    try {
      jdbcTemplate.queryForObject("SELECT 1", Integer.class);
      return ResponseEntity.ok(new StatusResponse("ready"));
    } catch (DataAccessException e) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(new StatusResponse("unavailable"));
    }
  }

  public record StatusResponse(String status) {}
}
