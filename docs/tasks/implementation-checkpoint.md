# Checkpoint de implementação paralela — fases 01–09 (pós-onda 3)

Atualizado: 2026-07-28

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
`781596c`, `42579ab`, `e08cce4` e `8d15c20`.

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

## Histórico do trabalho ativo no primeiro checkpoint

Os itens abaixo foram concluídos e são preservados apenas para rastreabilidade:

- O gate normal terminou com 92/92 testes.
- A worktree `/tmp/gnomon-w2-integration` produziu o commit `e594513`.
- O gate de integração terminou com 19/19 testes.
- As worktrees `/tmp/gnomon-w2-schema`, `/tmp/gnomon-w2-collaborators` e
   `/tmp/gnomon-w2-offerings` estavam limpas após os commits. Não há mudança pendente conhecida
   nelas.

## Próxima ação exata

Iniciar a onda 4 (fase 04 — guest booking) a partir do `main` após este checkpoint:

1. congelar primeiro os contratos compartilhados do módulo `booking` e a fronteira com
   `customers`, sem implementar;
2. criar a migration `V5__booking.sql` sob ownership exclusivo do agente principal;
3. abrir até três worktrees isoladas a partir do commit de contratos;
4. paralelizar:
   - domínio de slots, telefone E.164, fingerprint SHA-256 e invariantes;
   - persistência/transação/customer upsert/constraints;
   - API pública/idempotência e testes concorrentes;
5. integrar nessa ordem, substituir o `EmptyOccupiedSlotAdapter` pela leitura PostgreSQL e rodar
   os gates conjuntos antes de fechar a task 04.

Antes de implementar, reler `docs/tasks/04-guest-booking.md`, `docs/specs/booking.md` e ADRs
0008–0012, 0014, 0016 e 0017. A adição de libphonenumber exige justificativa documental.

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

## Sequência restante

### Onda 4 — booking

- Migration `V5__booking.sql`.
- Paralelizar:
  - domínio de slots, telefone E.164, fingerprint SHA-256 e invariantes;
  - persistência/transação/customer upsert/constraints;
  - API pública/idempotência/testes concorrentes.
- Adicionar libphonenumber com justificativa documentada.
- Garantir mesma chave/payload: `201` e replay `200`; chaves distintas no mesmo slot:
  `201` e `409`.
- Booking, customer e slots em transação curta; unique de slot é a garantia final.

### Ondas 5 e 6 — pós-core e ações públicas

- Onda 5: Redis fail-open, logging JSON/correlação/OTLP fail-open, painel admin/transições.
- Onda 6 começa quando surgir vaga:
  migration `V6__appointment_action_tokens.sql`, tokens, cancelamento/remarcação, hardening
  concorrente e regressões públicas.
- Cancelamento consome tokens após commit.
- Remarcação bem-sucedida rotaciona ambos; conflito preserva horário, slots e tokens.

### Onda 7 — gates finais

- GitHub Actions, JaCoCo e ArchUnit.
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
