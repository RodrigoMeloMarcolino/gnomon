# Fase 05 — Cache Redis das leituras públicas

Status: todo

## Objetivo

Reduzir carga nas leituras públicas quentes mantendo booking sempre em cálculo fresco, com
fail-open quando o Redis estiver indisponível.

## Escopo

- Primitives de cache em `shared.infrastructure.cache` (interface assíncrona simples,
  implementações Redis e no-op) — sem regra de domínio em shared (guardrail Moira).
- Política de cache dentro de cada módulo dono (padrão do checkpoint 2026-06-23 do Moira):
  - `catalog`: cache do perfil público do tenant, lista de calendários e catálogo;
  - `availability`: cache de available-slots com versionamento por calendário+dia.
- Invalidações:
  - create/update de offering → invalida catálogo do tenant;
  - mudança de duração/is_active em offering ou de availability rule → incrementa versão de
    agenda do calendário;
  - booking bem-sucedido → invalida chave exata consultada + versão calendário+data.
- Fallback: Redis indisponível → no-op transparente, API segue via PostgreSQL (WARN
  `cache.unavailable`); `/v1/ready` continua validando apenas PostgreSQL nesta fase.
- Booking nunca lê disponibilidade do cache (sempre cálculo fresco).

## Fora de escopo

- Cache de rotas administrativas; métricas de hit ratio (fase 06+); locks distribuídos.

## Testes

- Integração com Testcontainers Redis: hit/miss, invalidações acima, Redis derrubado → API
  continua 200, booking não usa cache.

## Critérios de aceite

- [ ] Leituras públicas cacheadas com invalidação correta após mutações.
- [ ] Fail-open demonstrado em teste.

## Notas de implementação

(preencher ao concluir)
