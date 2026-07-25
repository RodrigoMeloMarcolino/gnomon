# Fase 04 — Guest booking transacional

Status: todo

## Objetivo

O fluxo central do produto: cliente final agenda sem conta, com customer global reutilizado,
slots ocupados na mesma transação e double booking impossível via constraint.

## Escopo

- Migrations: `customers`, `appointments`, `appointment_slots` (spec booking seção 3) com
  `UNIQUE(phone)`, `UNIQUE(tenant_id, idempotency_key)` e
  **`UNIQUE(calendar_id, slot_start_at)`**. **Sem** `cancel_token_hash`/`reschedule_token_hash`
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

- [ ] Critérios da spec booking seção 9 verdes.
- [ ] Demonstração: dois requests simultâneos idênticos → um 201, um 409.
- [ ] Nenhuma violação de constraint conhecida retorna 500 (spec booking 6.1).

## Notas de implementação

(preencher ao concluir)
