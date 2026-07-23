# Fase 06 — Observabilidade (logs JSON + OTLP)

Status: todo

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

(preencher ao concluir)
