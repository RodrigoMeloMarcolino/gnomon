# Fase 05 — Cache Redis das leituras públicas

Status: doing

## Objetivo

Reduzir carga nas leituras públicas quentes mantendo booking sempre em cálculo fresco, com
fail-open quando o Redis estiver indisponível.

## Escopo

- Primitives de cache em `shared.infrastructure.cache` (interface simples,
  implementações Redis e no-op) — sem regra de domínio em shared (guardrail Moira).
- Política de cache dentro de cada módulo dono (padrão do checkpoint 2026-06-23 do Moira):
  - `catalog`: cache do perfil público do tenant, lista de calendários e catálogo, TTL
    configurável com default de 10 minutos;
  - `availability`: cache de available-slots com versionamento por calendário+dia e TTL
    configurável com default de 60 segundos.
- Invalidações:
  - create/update de offering → invalida catálogo do tenant;
  - mudança de duração/is_active em offering ou de availability rule → incrementa versão de
    agenda do calendário;
  - booking bem-sucedido → invalida chave exata consultada + versão calendário+data. **A data da
    chave/versão é sempre a data local do calendário (derivada de `start_at` via `ZoneId` do
    calendário), exatamente a mesma derivação usada na leitura** — dívida Moira: invalidação
    usava a data do offset do payload e apagava o dia errado, deixando slot recém-ocupado
    visível até o TTL (Emenda 00.5).
- Fallback: Redis indisponível → no-op transparente, API segue via PostgreSQL (WARN
  `cache.unavailable`); `/v1/ready` continua validando apenas PostgreSQL nesta fase.
- Booking nunca lê disponibilidade do cache (sempre cálculo fresco).

## Fora de escopo

- Cache de rotas administrativas; métricas de hit ratio (fase 06+); locks distribuídos.

## Testes

- Integração com Testcontainers Redis: hit/miss, invalidações acima, **booking em horário
  próximo da meia-noite local invalida o dia correto** (regressão da dívida Moira), Redis
  derrubado → API continua 200, booking não usa cache.

## Critérios de aceite

- [ ] Leituras públicas cacheadas com invalidação correta após mutações.
- [ ] Fail-open demonstrado em teste.
- [ ] Derivação de chave (data local do calendário) idêntica entre leitura e invalidação.

## Notas de implementação

- 2026-07-29: fase iniciada após o fechamento dos cinco gates da 04.5. O primeiro recorte é a
  primitive técnica fail-open em `shared.infrastructure.cache`, seguida de cache-aside por módulo;
  booking continuará lendo disponibilidade fresca e só invalidará após commit.
- 2026-07-29: adicionados `CacheStore`, adapters Redis/no-op e configuração por
  `gnomon.cache.enabled`. `RedisCacheStore` converte qualquer falha de acesso em miss/no-op e
  registra `cache.unavailable`; a compilação Java 21 passou. Ainda não há consumidor de cache,
  portanto os testes e as invalidações do catálogo/availability/booking permanecem pendentes.
- 2026-07-29: `RedisCacheStoreTest` cobre hit, miss, gravação com TTL, inicialização e avanço de
  versão, e falha `DataAccessException` em todas as operações. Os seis testes passaram com Java
  21 no contêiner Maven. A primitive usa o cliente síncrono `StringRedisTemplate`; o fail-open
  evita que esse detalhe técnico altere o fluxo HTTP quando Redis estiver indisponível.
- 2026-07-29: leituras públicas de perfil, calendários e offerings agora usam cache-aside
  versionado por tenant, com TTL configurável (`gnomon.cache.catalog.ttl`, padrão `10m`). A
  política fica no módulo `catalog`; suas mutações invalidam por incremento de versão somente
  após commit. A disponibilidade e o booking ainda não usam nem invalidam cache.
- 2026-07-29: `available-slots` agora usa cache-aside no módulo `availability`, com TTL
  configurável (`gnomon.cache.availability.ttl`, padrão `60s`). A chave inclui tenant,
  calendário e a `LocalDate` já interpretada no fuso do calendário; regras de disponibilidade
  avançam a versão global do calendário somente após commit, invalidando seus dias em cache. O
  próximo recorte deve adicionar a versão específica calendário+dia e a invalidação pós-commit
  de booking, derivando a data local a partir de `start_at` e do `ZoneId` do calendário.
- 2026-07-30: a chave de `available-slots` passou a incluir `offeringId`, evitando colisão entre
  serviços de durações diferentes. A invalidação de booking agenda, exclusivamente após commit,
  a remoção da chave `calendar + dia local + offering` e o avanço das versões global e diária;
  a data é derivada por `startAt.atZone(calendarZone).toLocalDate()`. Adapters de catálogo e
  booking consomem o input port de availability, mantendo ADR 0019: regras, calendário,
  assignment e duração/atividade de offering invalidam os calendários afetados; update de tenant
  invalida o perfil público via input port de catálogo. Ainda faltam os cenários de integração
  com Redis real e os gates completos; por isso a fase permanece `doing` e os critérios não foram
  marcados como concluídos.
