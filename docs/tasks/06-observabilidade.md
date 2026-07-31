# Fase 06 — Observabilidade (logs JSON + OTLP)

Status: doing

## Objetivo

Contrato completo de logging estruturado vendor-neutral em produção, com pipeline local
Collector→Loki→Grafana validada.

## Escopo

Conforme `docs/specs/structured-logging.md`:

- Encoder JSON no Logback emitindo o contrato da seção 2 da spec; modo console em `local`.
- Filtro de borda HTTP: `request_id`/`correlation_id`, MDC, headers de resposta, exatamente um
  access log terminal por request, 500 com envelope seguro (access log do Tomcat desligado).
- Appender OTLP opcional: batch assíncrono, fila limitada, fail-open, ciclo de vida pelo
  contexto Spring, shutdown idempotente.
- Redação recursiva de chaves sensíveis; política de dados sensíveis aplicada.
- Catálogo de eventos P0/P1 instrumentado nos módulos (fundação, booking, tenancy, admin,
  cache).
- `docker-compose.observability.yaml` (Collector/Loki/Grafana) + script de smoke do caminho
  completo.

## Fora de escopo

- Métricas Micrometer/Prometheus e tracing distribuído (spec/ADR próprios futuros); alertas e
  SLOs; rate limiting.

## Testes

- Bateria da spec seção 9 (formato, unicidade do access log, correlação, redação, fail-open).
- Smoke Docker validado com a stack real.

## Critérios de aceite

- [ ] Critérios da spec seção 10 verdes.
- [ ] Consulta LogQL por `correlation_id` retorna a jornada completa de um booking.

## Notas de implementação

- Fundação em andamento (2026-07-31): formatter JSON próprio com contrato OTel, fachada SLF4J,
  filtro de correlação/access log, headers CORS e eventos de lifecycle já foram adicionados.
- Continuação (2026-07-31): a borda HTTP agora tem teste de correlação válida/inválida,
  access log terminal único e envelope 500 seguro; o formatter preserva stack trace apenas no
  evento estruturado. Eventos de tenancy, provisionamento local, catálogo e availability rules
  são emitidos somente após commit, com IDs técnicos e sem PII.
- O contrato de erro da spec foi sincronizado para `internal_server_error`, que já é o código
  efetivamente devolvido pelos exception handlers.
- Ainda pendentes para encerrar a fase: validar o appender OTLP fail-open e executar o smoke
  real de booking pela stack Collector→Loki→Grafana.

## Follow-up registrado (2026-07-24 — semente do Moonlight)

O catálogo de eventos P1 de agenda (`appointment.created/cancelled/completed/no_show`) deve
ser desenhado como **contrato estável e sem PII** (payload: ids, instantes, tenant_id, status;
nunca nome/telefone de customer). Motivo: esses eventos são os candidatos naturais a
alimentar o produto futuro **Moonlight** (métricas cross-tenant) via outbox/CDC, sem
analytics no OLTP. Ver `ephemeris/docs/products/moonlight.md`.
