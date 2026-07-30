# Checkpoint de implementação paralela — fases 01–09 (pós-onda 4)

Atualizado: 2026-07-29

Este arquivo é o handoff operacional para retomar a construção do backend em outra sessão sem
reconstituir o contexto da conversa. A fonte normativa continua sendo o PRD, os ADRs, as specs e
as tasks deste repositório.

## Regras de retomada

- Usar somente skills versionadas em `.codex/skills/`; não usar skills globais, pessoais ou de
  plugins.
- Implementações continuam paralelizadas em até três subagentes, com worktrees isoladas em
  `/tmp`.
- O agente principal é o único owner de integração, documentação, configurações globais,
  migrations compartilhadas e checkpoints no Ephemeris.
- Não executar Maven concorrentemente na mesma worktree.
- Cada onda só avança depois de `spotless:check`, testes, ArchUnit e integração verdes.
- O host expõe Java 17; executar os gates com Java 21 no container
  `maven:3.9-eclipse-temurin-21`.

## Estado integrado em `main`

HEAD no primeiro registro deste checkpoint: `73d5585`. A onda 2 foi fechada depois nos commits
`83fb81b` (este checkpoint), `e594513` (integração de catálogo) e `36cb4a4` (documentação).
Os contratos da onda 3 foram congelados em `dd6cfef`. A fase 03 foi integrada nos commits
`781596c`, `42579ab`, `e08cce4` e `8d15c20`. A fase 04 foi integrada a partir dos contratos
`5bc786e`, migration `67ee031` e frentes `837c5ff`, `0caf214` e `bd9b40b`.

### Fase 01 — concluída

- Resource server Keycloak com issuer público, JWKS interno, audience, CORS e autorização local.
- JIT de usuário, tenants e memberships tenant-scoped.
- Migration `V2__identity_and_tenancy.sql`.
- APIs administrativas e envelope de erro canônico.
- Teste com Keycloak real e teste ponta a ponta SecurityFilterChain → JIT → controller →
  PostgreSQL.
- Lookup de e-mail `CITEXT` corrigido para igualdade nativa sem `UPPER`.
- Documentação da fase 01 já marcada como concluída.

Commits relevantes:

- `5a12826`, `2af8194`, `bfba08c`, `d962bda`, `2c4a031`, `89ba3d1`, `85f3515`
- `22e4106` — integração de identidade/tenancy
- `5aa21e0` — correção do lookup `CITEXT`
- `f160980` — checkpoint documental da fase 01

### Fase 02 — concluída

- Migration `V3__catalog.sql` com collaborators, calendars, offerings e
  calendar_offerings.
- Colaborador cria calendário 1:1 na mesma transação.
- CRUD tenant-scoped, soft delete e vínculo de usuário `staff`.
- Desativar colaborador revoga apenas membership `staff`; owner/admin são preservados.
- Link concorrente/idempotente de staff usa `INSERT ... ON CONFLICT DO NOTHING`.
- CRUD de offerings, preço em centavos, duração múltipla de 15, unicidade de título ativo.
- `PUT` de atribuições calendário–offering.
- Perfil, calendários e offerings públicos, incluindo filtro por calendário.
- `CatalogExceptionHandler` traduz catálogo para o envelope de erro e status 4xx.

Commits relevantes:

- `2ff6795` — V3
- `0333f45` — colaboradores/calendários
- `0e72075` — core de offerings/atribuições
- `29fd86a` — APIs de catálogo e handler
- `98ba294` — hardening do lifecycle staff
- `73d5585` — wiring/validação de offerings

Validações finais verdes:

- Suíte conjunta: 92 testes normais, incluindo 4 ArchUnit.
- Perfil `integration`: 19 testes com PostgreSQL 16 e Keycloak 26.
- Flyway V1–V3, `ddl-auto=validate` e a jornada integrada do catálogo passaram.

### Fase 03 — concluída

- Migration `V4__availability_rules.sql` tenant-scoped, com FK composta para calendários,
  CHECKs nomeados, índices e trigger de `updated_at`.
- CRUD administrativo com soft deactivate e autorização owner/admin/staff do próprio calendário.
- Cálculo puro de disponibilidade em slots de 15 minutos, incluindo regras sobrepostas,
  ocupação, passado, data local e DST gap/overlap.
- Endpoint público de `available-slots` com catálogo ativo/atribuído e resposta UTC.
- `OccupiedSlotPort` permanece vazio apenas até a fase 04.
- Gate normal: 129/129 testes; ArchUnit: 4/4.
- Perfil `integration`: 23/23 com PostgreSQL 16, Keycloak 26, Flyway V1–V4 e
  `ddl-auto=validate`.

### Fase 04 — concluída

- Migration `V5__booking.sql` com customers globais, appointments tenant-scoped e slots
  ocupados protegidos por `UNIQUE(calendar_id, slot_start_at)`.
- Domínio puro de slots, telefone E.164 e fingerprint SHA-256; libphonenumber com região default
  configurável.
- Booking público transacional com `Idempotency-Key` obrigatório, replay `200`, criação `201`,
  conflito determinístico e customer upsert concorrente.
- `OccupiedSlotPort` agora lê PostgreSQL; disponibilidade deixa de usar o adapter vazio.
- Eventos P0 de booking sem PII.
- Gate normal: 211/211 testes; ArchUnit: 4/4.
- Perfil `integration`: 33/33 com PostgreSQL 16, Keycloak 26, Flyway V1–V5,
  `ddl-auto=validate` e corridas reais de slot/idempotência/customer.

## Histórico do trabalho ativo no primeiro checkpoint

Os itens abaixo foram concluídos e são preservados apenas para rastreabilidade:

- O gate normal terminou com 92/92 testes.
- A worktree `/tmp/gnomon-w2-integration` produziu o commit `e594513`.
- O gate de integração terminou com 19/19 testes.
- As worktrees `/tmp/gnomon-w2-schema`, `/tmp/gnomon-w2-collaborators` e
   `/tmp/gnomon-w2-offerings` estavam limpas após os commits. Não há mudança pendente conhecida
   nelas.

## Checkpoint 04.5 — hardening arquitetural (concluído)

Antes das ondas 05–07, a fase 04.5 consolidou a taxonomia de ports, customers como módulo
próprio, principal em `shared.security`, contratos de integração explícitos e gates ArchUnit
reforçados (ADR 0019), sem alterar endpoints, JSON ou migrations. Em 2026-07-29, os gates
Spotless, unitário (213), ArchUnit isolado (5), integração (33) e all-tests (246) passaram com
Java 21 em contêiner.

## Próxima ação exata

Continuar a fase 05, com o core transacional estável:

1. [x] escrever testes unitários para `RedisCacheStore` (hit, miss, TTL/versionamento e falha
   `DataAccessException`); os seis testes isolados passaram com Java 21;
2. implementar cache-aside de perfil, calendários e offerings no módulo `catalog`, com chaves e
   TTL configuráveis pertencentes ao módulo;
3. só então cachear `available-slots`, usando a data local do calendário para chaves e versões;
4. registrar invalidações após commit das mutações e do booking, cobrir Redis real em
   Testcontainers e só fechar a fase após os gates completos.

1. reler `docs/tasks/05-cache-redis.md`, `06-observabilidade.md` e
   `07-painel-admin.md`, além das specs/ADRs apontadas por elas;
2. congelar contratos compartilhados de invalidação de cache, contexto estruturado de logs e
   leitura/transição administrativa de appointments;
3. abrir worktrees isoladas a partir do commit de contratos e paralelizar Redis fail-open,
   observabilidade vendor-neutral e painel admin tenant-scoped;
4. manter migrations/configurações globais, integração e documentação sob ownership do agente
   principal;
5. integrar somente depois dos gates isolados e executar `spotless:check`, testes, ArchUnit e
   integração conjunta antes de fechar qualquer uma das fases 05–07.

## Workstream 07.5 — portfólio (contrato documental congelado)

O portfólio é paralelo às fases 05–08 e bloqueia a fase 09. A etapa de 2026-07-29 foi somente
documental: não existem classes, migrations, dependências ou configuração de infraestrutura.
Os documentos normativos são `docs/features/tenant-portfolio/`, task 07.5 e ADRs 0020–0021.

- Reservar `V6__tenant_portfolio.sql`; a futura migration da fase 08 é
  `V7__appointment_action_tokens.sql`.
- Criar o futuro módulo `io.gnomon.portfolio` na direção `api → application → domain ←
  infrastructure`; o port de tenancy é a fronteira com identidade e AWS SDK fica somente no
  adapter S3-compatible.
- Administração é owner/admin; staff é negado. Bucket e master são privados; backend decide toda
  leitura pública de `display`/`thumbnail`.
- Seguir as doze entregas do plano, sem pular os testes PostgreSQL, Garage, processamento adverso,
  reconciliação e smoke completo.

A fase 08 pode começar quando houver vaga, mas cancelamento/remarcação continuam dependentes dos
tokens da migration aditiva própria e das regras transacionais descritas no checkpoint abaixo.

## Registro dos contratos congelados da onda 3

Pacote: `io.gnomon.availability`.

- `AvailabilityWindow(DayOfWeek, LocalTime, LocalTime, boolean)`.
- `AvailabilityCalculator.availableStarts(rules, durationMinutes, occupied, date, zone, now)`.
- `AvailabilityRuleRepository`: save/lookups tenant-scoped e consulta de regras ativas.
- `AvailabilityCalendarAccessPort.requireWritableCalendar(...)`.
- `PublicAvailabilityCatalogPort.requireSchedulableOffering(...)`.
- `OccupiedSlotPort.findOccupied(...)`; adapter vazio na fase 03, PostgreSQL na fase 04.
- `AvailabilityException(code, message)`.
- Endpoint:
  `GET /v1/public/tenants/{slug}/available-slots?calendar_id=&offering_id=&date=`.
- JSON exato: `{"available_start_times":[...]}`.
- DST: usar `ZoneRules.getValidOffsets`; gap gera zero instante e overlap gera os dois instantes
  UTC; deduplicar e ordenar.

Ownership executado:

- Subagente A: algoritmo puro, slotização, DST e testes unitários.
- Subagente B: migration V4, aggregate/regra, JPA, CRUD admin, constraint translation e testes
  PostgreSQL.
- Subagente C: resolução de catálogo, occupied port vazio, endpoint público e testes de contrato.

O commit `dd6cfef` congelou esses contratos antes da abertura das três worktrees; os resultados
foram integrados sem mudança de contrato.

## Registro dos contratos congelados da onda 4

Pacote: `io.gnomon.booking`.

- `CreateAppointmentUseCase.create(CreateAppointmentCommand)` retorna
  `CreationResult(AppointmentResult, replayed)`.
- `BookingCatalogPort` resolve tenant, calendário e offering agendável em um contexto snapshot.
- `BookingAvailabilityPort` valida o instante usando regras e slots ocupados.
- `CustomerRepository.findOrCreate(...)` executa o upsert global por telefone.
- `AppointmentRepository` faz lookup/insert idempotente e insere slots ocupados.
- `SlotGenerator`, `PhoneCanonicalizer` e `AppointmentFingerprint` são contratos de domínio
  puros.
- Endpoint: `POST /v1/public/tenants/{slug}/appointments`, com payload `snake_case` e
  `Idempotency-Key`.
- `OccupiedSlotPort` manteve o contrato da onda 3; apenas o adapter vazio foi trocado por
  PostgreSQL.

O commit `5bc786e` congelou esses contratos antes das três worktrees. A integração preservou as
assinaturas; apenas adicionou implementações, wiring e testes conjuntos.

## Sequência restante

### Ondas 5 e 6 — pós-core e ações públicas

- Onda 5: Redis fail-open, logging JSON/correlação/OTLP fail-open, painel admin/transições.
- Onda 6 começa quando surgir vaga:
  migration `V7__appointment_action_tokens.sql`, tokens, cancelamento/remarcação, hardening
  concorrente e regressões públicas.
- Cancelamento consome tokens após commit.
- Remarcação bem-sucedida rotaciona ambos; conflito preserva horário, slots e tokens.

### Onda 7 — gates finais

- GitHub Actions, JaCoCo e ArchUnit, após 05 + 06 + 07 + 07.5 + 08.
- Schema drift, constraints e `EXPLAIN`.
- Smoke E2E:
  tenant → colaborador → offering → atribuição → disponibilidade → booking → conflito →
  cancelamento/remarcação.
- Metas: 80% linhas e 70% branches em `domain` e `application`.

## Checkpoint organizacional final

Ao concluir cada fase, sincronizar a task local. Ao concluir o trabalho:

- atualizar `../ephemeris/docs/services/gnomon.md`;
- atualizar `../ephemeris/docs/roadmap.md`;
- usar a data real do checkpoint;
- manter apenas status e ponteiros no Ephemeris, sem copiar conteúdo normativo;
- a escrita no repo irmão pode exigir autorização adicional do sandbox.
