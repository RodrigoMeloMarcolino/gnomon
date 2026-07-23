# Spec — Logging estruturado vendor-neutral

Status: aprovada para implementação
ADR relacionado: 0015
Origem: adaptação da spec homônima do Moira (incluindo hardening de 2026-06-26) para
SLF4J/Logback/OpenTelemetry Java

---

## 1. Objetivo

Responder rapidamente: qual requisição falhou, em qual rota, qual etapa de negócio foi
concluída/rejeitada/em conflito, quais dependências estavam degradadas e quanto durou — sem
registrar PII ou credenciais — e exportar para Loki/outro backend sem tocar controllers nem
use cases.

## 2. Contrato de evento (compatível com OTel Logs Data Model)

Um evento por linha em stdout, JSON, com:

| Campo | Descrição |
| ----- | --------- |
| `timestamp` | ISO 8601 UTC com milissegundos |
| `severity_text` / `severity_number` | INFO, WARN, ERROR… / número OTel |
| `body` | mensagem humana curta |
| `event_name` | nome estável do evento (ex.: `http.request.completed`) |
| `service.name` | `gnomon` (resource) |
| `trace_id` / `span_id` | quando tracing ativo (opcional nesta fase) |
| `attributes` | metadata estruturada: `request.id`, `correlation.id`, `http.route`, `http.status_code`, `duration_ms`, IDs de domínio (`tenant.id`, `calendar.id`, `appointment.id`), `error.type` |

Mapeamento para Loki: labels apenas de baixa cardinalidade (`service_name`, `event_name`,
`severity`); IDs e correlacionais são structured metadata, nunca labels.

## 3. Contexto de requisição

- Filtro na borda HTTP gera `request_id` (UUID novo por request).
- `correlation_id`: vem de `X-Correlation-ID` (ou `X-Request-ID`) do chamador quando válido
  (charset/tamanho limitados); senão assume o `request_id`.
- Ambos em MDC durante toda a requisição e devolvidos nos headers de resposta
  `X-Request-ID` / `X-Correlation-ID`.
- **Exatamente um access log terminal por requisição**: `http.request.completed` ou
  `http.request.failed`, com route template (ex.: `/v1/public/tenants/{slug}/appointments`),
  método, status e `duration_ms`. O access log do Tomcat é desligado para não duplicar.
- Resposta 500 segura: envelope padrão `{"error": {"code": "internal_error", ...}}`, sem stack
  trace no body; stack apenas no log.

## 4. Níveis

| Nível | Uso |
| ----- | --- |
| DEBUG | diagnóstico detalhado, desligado fora de `local` |
| INFO | eventos de negócio concluídos, access log, lifecycle |
| WARN | rejeições de domínio esperadas (409/422), dependência degradada com fallback (cache miss por Redis down) |
| ERROR | falhas inesperadas, 500, dependência sem fallback |
| FATAL | falha de startup (aplicação não sobe) |

## 5. Exporters

- **stdout JSON obrigatório** (Logback com encoder JSON).
- **OTLP opcional**: `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` definido habilita appender OTLP com
  batch assíncrono, fila limitada e **fail-open** (erro de exportação nunca afeta request nem
  readiness).
- Cada ambiente escolhe OTLP direto **ou** coleta de stdout pelo agente da plataforma — nunca
  ambos para o mesmo backend.
- Ciclo de vida do exporter OTLP gerenciado pelo contexto Spring; shutdown idempotente.

Configuração:

```text
LOG_LEVEL=INFO
LOG_FORMAT=json          # console em dev local
OTEL_SERVICE_NAME=gnomon
OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://localhost:4318/v1/logs
OTEL_EXPORTER_OTLP_TIMEOUT=5
```

## 6. Política de dados sensíveis

Proibido registrar: senhas, tokens (Keycloak, cancel/reschedule), `Idempotency-Key` crua,
payloads de request, telefone/e-mail/nome de customers, URLs completas de banco/cache,
segredos. Redação recursiva para chaves conhecidas (`password`, `token`, `authorization`,
`key`, `secret`) quando estruturas forem logadas.

## 7. Catálogo de eventos

Fundação (P0): `http.request.completed`, `http.request.failed`, `application.started`,
`application.ready`, `application.shutdown`.

Booking (P0): `appointment.booking_succeeded`, `appointment.booking_replayed`,
`appointment.booking_rejected`, `appointment.booking_conflict`.

Tenancy/auth (P0): `tenant.created`, `membership.added`, `membership.removed`,
`auth.user_provisioned`, `auth.access_denied`.

Admin (P1): `offering.created/updated`, `collaborator.created/updated`,
`calendar.created/updated`, `availability_rule.created/updated`, `appointment.cancelled`,
`appointment.completed`, `appointment.no_show`.

Cache (P1): `cache.hit`, `cache.miss`, `cache.unavailable` (WARN com fallback), `cache.invalidated`.

## 8. Pipeline local

`docker-compose.observability.yaml` (preservado do Moira): Collector OTLP em `localhost:4318`,
Loki em `localhost:3100`, Grafana em `http://localhost:3000` (admin/admin dev). Consulta por
correlação:

```logql
{service_name="gnomon"} | correlation_id="operation-123"
```

## 9. Testes

- Formato JSON válido com todos os campos obrigatórios do contrato.
- Um único access log por request (incluindo erros 4xx/5xx).
- `correlation_id` válido do chamador preservado; inválido substituído.
- Redação de chaves sensíveis.
- Falha do endpoint OTLP não afeta requests (fail-open) nem readiness.
- 500 retorna envelope seguro e loga stack trace uma única vez.

## 10. Critérios de aceite

- [ ] stdout JSON no contrato da seção 2 em todos os perfis (exceto `local` em modo console).
- [ ] Access log único com correlação funcional.
- [ ] OTLP opcional, assíncrono e fail-open.
- [ ] Smoke Docker Collector→Loki→Grafana validado.
