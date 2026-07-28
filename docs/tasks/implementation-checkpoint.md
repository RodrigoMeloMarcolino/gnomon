# Checkpoint de implementação paralela — fases 01–09

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
Os contratos da onda 3 foram congelados em `dd6cfef`.

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

## Histórico do trabalho ativo no primeiro checkpoint

Os itens abaixo foram concluídos e são preservados apenas para rastreabilidade:

- O gate normal terminou com 92/92 testes.
- A worktree `/tmp/gnomon-w2-integration` produziu o commit `e594513`.
- O gate de integração terminou com 19/19 testes.
- As worktrees `/tmp/gnomon-w2-schema`, `/tmp/gnomon-w2-collaborators` e
   `/tmp/gnomon-w2-offerings` estavam limpas após os commits. Não há mudança pendente conhecida
   nelas.

## Próxima ação exata

Integrar, nesta ordem lógica, os resultados das três worktrees ativas da onda 3:

- `/tmp/gnomon-w3-algorithm`, branch `agent/w3-algorithm`: cálculo puro, slots e DST;
- `/tmp/gnomon-w3-rules`, branch `agent/w3-rules`: V4, JPA, CRUD admin e handler;
- `/tmp/gnomon-w3-public`, branch `agent/w3-public`: adapter de catálogo, occupied vazio e
  endpoint público.

Antes de cada cherry-pick, exigir commit atômico, gates focados e worktree limpa. Depois de
integrar os três, resolver somente incompatibilidades reais, executar a suíte conjunta e fechar a
task 03.

## Contratos congelados para a onda 3

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

Ownership proposto:

- Subagente A: algoritmo puro, slotização, DST e testes unitários.
- Subagente B: migration V4, aggregate/regra, JPA, CRUD admin, constraint translation e testes
  PostgreSQL.
- Subagente C: resolução de catálogo, occupied port vazio, endpoint público e testes de contrato.

Criar primeiro um commit pequeno apenas com os contratos compartilhados, validá-lo e então abrir
três worktrees a partir desse HEAD.

## Sequência restante

### Onda 3 — disponibilidade

- Implementar A/B/C conforme ownership acima.
- Validar horário desalinhado na borda e nos CHECKs.
- Testar exact fit, regras sobrepostas, ocupado, passado, data local e DST.
- Fechar documentação da fase 03.

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
