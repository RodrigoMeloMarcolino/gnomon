# ADR 0015 — Logging estruturado vendor-neutral + OTLP

Status: Accepted
Data: 2026-07-23

## Contexto

O Moira consolidou (ADR 0015 + spec + hardening): um evento de log por linha, contrato
compatível com OpenTelemetry Logs Data Model, stdout JSON obrigatório, OTLP opcional fail-open,
Collector como fronteira de roteamento (Loki/Grafana), proibição de SDK proprietário nos
módulos de negócio e regras estritas de cardinalidade e dados sensíveis. Essa arquitetura é
vendor-neutral e independe da stack — deve ser preservada no Gnomon com tecnologia Java.

## Decisão

1. **SLF4J como API interna de log** (facade padrão do Spring); implementação Logback.
2. **Contrato de evento alinhado ao OTel Logs Data Model**: timestamp, severity text/number,
   body, `event_name`, resource e attributes estruturados (ver
   `docs/specs/structured-logging.md`).
3. **JSON Lines em stdout obrigatório** (encoder JSON no Logback). **OTLP opcional** via
   OpenTelemetry SDK/appender, com envio assíncrono, fila limitada e **fail-open** (falha
   remota nunca afeta requests nem readiness).
4. **Exatamente um access log terminal por requisição** (`http.request.completed` /
   `http.request.failed`) com route template e `duration_ms`, emitido por filtro na borda HTTP
   (substitui o access log padrão do Tomcat para evitar duplicidade).
5. `X-Request-ID` (UUID novo por request) e `X-Correlation-ID` (preservado quando válido)
   propagados em MDC e devolvidos nos headers de resposta.
6. **Proibido** registrar: credenciais, tokens (Keycloak ou de appointment), chaves de
   idempotência, payloads, dados pessoais, URLs de banco/cache. Labels de baixa cardinalidade;
   IDs de domínio são structured metadata, não labels.
7. Collector/Loki/Grafana locais via `docker-compose.observability.yaml` (preservado). Métricas
   Micrometer/Prometheus e tracing são evoluções com spec própria.

## Consequências

- Domínio e use cases não conhecem setup/exporters de logging; apenas a API SLF4J.
- Configuração por ambiente: `LOG_LEVEL` e variáveis `OTEL_*`; o formato estruturado é definido
  pelo perfil Spring, sem a variável inoperante `LOG_FORMAT`. OTLP só é ativado com
  `OTEL_LOGS_EXPORTER=otlp`; o timeout é `OTEL_EXPORTER_OTLP_LOGS_TIMEOUT`.
- Cada ambiente escolhe OTLP direto **ou** coleta de stdout para um mesmo backend — nunca
  ambos (ingestão duplicada).

## Rastreabilidade

- Herda integralmente: Moira ADR 0015 e `docs/specs/structured-logging.md` (incluindo o
  hardening de 2026-06-26), adaptados de `logging`/stdlib para SLF4J/Logback/OTel Java.
