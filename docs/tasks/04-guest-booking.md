# Fase 04 — Guest booking transacional

Status: done

## Objetivo

O fluxo central do produto: cliente final agenda sem conta, com customer global reutilizado,
slots ocupados na mesma transação e double booking impossível via constraint.

## Escopo

- Migrations: `customers`, `appointments`, `appointment_slots` (spec booking seção 3) com
  `UNIQUE(phone)`, `UNIQUE(tenant_id, idempotency_key)` e
  **PRIMARY KEY(tenant_id, calendar_id, slot_start_at)**. **Sem** `cancel_token_hash`/`reschedule_token_hash`
  (nascem na fase 08 — ADR 0017); `status` com `CHECK IN (...)`; índices de todas as FKs.
- Domínio puro: `generateSlots` (spec booking 4.1) e `canonicalPhone` (4.3).
- `CreateAppointmentUseCase` completo (spec booking 5.2): validações de escopo/atribuição,
  canonização, validação de disponibilidade, **`Idempotency-Key` obrigatório (ADR 0014)**,
  idempotência por fingerprint, transação curta (customer + appointment + slots), tradução de
  violação de constraint para 409.
- **Tabela de tradução determinística de constraints (spec booking 6.1 / ADR 0016): toda
  UNIQUE/CHECK/FK criada aqui tem 4xx estável; `start_at` com segundos não-zero → 422 (nunca
  truncar).**
- Endpoint público `POST /v1/public/tenants/{slug}/appointments` (ADR 0014).
- Eventos de log P0 do booking (spec structured-logging seção 7).

## Fora de escopo

- Cancelamento/remarcação (fase 08); confirmação de telefone; notificações; cache (fase 05).

## Testes (todos da spec booking seção 7)

- Unit de domínio e de use case.
- Integração com PostgreSQL real (Testcontainers): concorrência no mesmo slot (um vence),
  overlap parcial, calendários diferentes em paralelo (ambos vencem), rollback total, corrida
  de customer por telefone, replay/conflito de idempotência, **corrida com a mesma
  `Idempotency-Key` (uma cria, outra faz replay — um só appointment persiste)**.

## Critérios de aceite

- [x] Critérios da spec booking seção 9 verdes.
- [x] Demonstração: mesma chave + mesmo payload simultâneos → um 201 e um replay 200; chaves
      distintas disputando o mesmo slot → um 201 e um 409.
- [x] Nenhuma violação de constraint conhecida retorna 500 (spec booking 6.1).

## Notas de implementação

- Migration `V5__booking.sql` cria `customers`, `appointments` e `appointment_slots`, com FKs
  tenant-scoped, índices de todas as FKs, triggers reais de `updated_at`, status fechado e as
  uniques de telefone, idempotência e ocupação por calendário. Tokens da fase 08 permanecem
  ausentes e `appointment_slots` é append-only, sem `updated_at`.
- O módulo `io.gnomon.booking` preserva domínio puro, ports de application e adapters JDBC. O
  fluxo público valida catálogo e disponibilidade, normaliza o payload, calcula fingerprint
  SHA-256, faz upsert concorrente de customer, persiste appointment + slots em transação curta
  e materializa replay sem chamadas externas.
- A dependência `com.googlecode.libphonenumber:libphonenumber:9.0.32` foi adicionada porque
  canonização E.164 correta depende de metadados internacionais atualizados; heurísticas locais
  para DDI/DDD não cobrem validade, regiões e evolução dos planos de numeração. A região default
  é configurável por `DEFAULT_PHONE_REGION` (default `BR`).
- `EmptyOccupiedSlotAdapter` foi removido. Disponibilidade pública e validação de booking agora
  leem `appointment_slots` no PostgreSQL através de `PostgresOccupiedSlotAdapter`.
- O endpoint `POST /v1/public/tenants/{slug}/appointments` usa payload `snake_case`, exige
  `Idempotency-Key`, retorna `201` na criação e `200` no replay. Precheck indisponível retorna
  `422 slot_unavailable`; disputa decidida pela PK do banco retorna
  `409 slot_unavailable`.
- Eventos P0 `appointment.booking_succeeded`, `appointment.booking_replayed`,
  `appointment.booking_rejected` e `appointment.booking_conflict` foram adicionados sem PII.
  O contrato JSON completo do stdout continua pertencendo à fase 06.

## Validação

- `./mvnw spotless:check`: verde em container Maven/Java 21.
- `./mvnw test`: 211/211 testes verdes, incluindo 4/4 regras ArchUnit.
- `./mvnw verify -Pintegration`: 33/33 testes verdes com PostgreSQL 16, Keycloak 26, Flyway
  V1–V5 e `ddl-auto=validate`.
- Concorrência real: mesma chave/payload → `201` + `200` e um appointment; chaves distintas no
  mesmo slot → `201` + `409`; overlap parcial → um vencedor; calendários diferentes → ambos
  vencem; corrida do mesmo telefone → um customer global.

## Riscos/follow-ups

- Formatação estruturada JSON, correlação e OTLP dos eventos entram na fase 06.
- Rate limiting público e gates de carga/schema drift permanecem para o hardening da fase 09.
- Se os gatilhos do ADR 0022 forem atingidos, avaliar e executar somente pelos gates do
  [runbook de migração GiST](../specs/appointment-gist-migration.md).
