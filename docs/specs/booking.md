# Spec — Core de booking (slots, disponibilidade, criação transacional)

Status: aprovada para implementação
ADRs relacionados: 0006, 0007, 0008, 0009, 0010, 0011, 0012, 0014
Origem: livedoc seções 9–12, 15.6.1, 16.4–16.5, 17 (testes manuais do core)

---

## 1. Objetivo

Implementar o coração do produto: cálculo dinâmico de disponibilidade por calendário e criação
de appointment com prevenção de double booking garantida pelo banco, idempotência e reúso de
customer — tudo com testes fortes.

## 2. Escopo

- Geração de slots discretos de 15 minutos.
- Cálculo dinâmico de disponibilidade (sem persistir slots livres).
- Criação síncrona transacional de appointment (+ customer + slots ocupados).
- Idempotência via `Idempotency-Key`.
- Tradução de conflito de concorrência para 409.

Fora de escopo: cancelamento/remarcação (fase 08), reserva temporária com TTL, confirmação de
telefone.

## 3. Modelo de dados (tabelas do fluxo)

### `offerings`

`id`, `tenant_id` (FK), `title` (120), `description` (null), `duration_minutes` (>0 e %15=0),
`price_cents` (null ou >=0), `is_active` (default true), timestamps.
Constraints: `UNIQUE(tenant_id, lower(title)) WHERE is_active`; índice `(tenant_id, is_active)`.

### `calendar_offerings`

PK composta `(calendar_id, offering_id)`; ambas FKs.

### `availability_rules`

`id`, `calendar_id` (FK), `weekday` SMALLINT (1–7, 1=segunda), `start_time` < `end_time`
(TIME local), `is_active` (default true), timestamps. Índice `(calendar_id, weekday)`.

### `customers`

`id`, `name` (120), `phone` (32, UNIQUE — canônico E.164), `email` (CITEXT, null),
`phone_verified_at` (null), timestamps.

### `appointments`

`id`, `tenant_id` (FK), `calendar_id` (FK), `offering_id` (FK), `customer_id` (FK),
`start_at` < `end_at` (TIMESTAMPTZ), `duration_minutes_snapshot` (>0, %15=0),
`calendar_timezone_snapshot` (IANA), `status` (default 'scheduled', IN
('scheduled','cancelled','completed','no_show')), `customer_notes` (null),
`idempotency_key` (null), `idempotency_fingerprint` (null),
`cancel_token_hash` (null), `reschedule_token_hash` (null), timestamps.
Constraints: `UNIQUE(tenant_id, idempotency_key)`.
Índices: `(calendar_id, start_at)`, `(tenant_id, status, start_at)`, `(customer_id)`.

### `appointment_slots`

`id`, `appointment_id` (FK), `calendar_id` (FK), `slot_start_at` (TIMESTAMPTZ).
Constraint crítica: **`UNIQUE(calendar_id, slot_start_at)`**.

## 4. Regras de domínio (funções puras — módulo `booking.domain`)

### 4.1 Geração de slots

```
generateSlots(startAt: Instant, durationMinutes: int): List<Instant>
```

- `durationMinutes` deve ser múltiplo positivo de 15, senão erro de domínio.
- `startAt` alinhado a boundary de 15 min (minuto ∈ {0,15,30,45}, segundos/nanos zerados),
  senão erro de domínio.
- Retorna `[startAt, startAt+15m, ..., startAt + duration - 15m]` (30min→2, 45min→3, 60min→4,
  4h→16).

### 4.2 Cálculo de disponibilidade

```
availableStarts(rules: List<AvailabilityRule>, durationMinutes: int,
                occupied: Set<Instant>, date: LocalDate, zone: ZoneId, now: Instant): List<Instant>
```

1. Filtra regras ativas do `weekday` da data local; sem regra → lista vazia.
2. Gera candidatos em step de 15 min dentro de cada janela `[start_time, end_time)` local,
   convertidos para UTC via `zone`; regras sobrepostas são deduplicadas.
3. Remove candidatos cujo serviço não cabe na janela (`candidato + duração > end_time`).
4. Remove candidatos cujos slots gerados intersectam `occupied`.
5. Remove candidatos ≤ `now`.
6. Retorna instantes UTC ordenados.

### 4.3 Canonização de telefone

```
canonicalPhone(raw: String): String  // E.164 ou erro de domínio phone_invalid
```

- Aceita dígitos, `+`, espaços/hífens/parênteses na entrada; normaliza para E.164.
- Default de país por configuração (`DEFAULT_PHONE_REGION`, ex.: `BR`) quando não internacional.
- Persistência e busca sempre na forma canônica.

## 5. Casos de uso

### 5.1 `ListAvailableSlotsUseCase`

Entrada: `tenantSlug`, `calendarId`, `offeringId`, `date`.
Regras:

- tenant existe e está ativo (por slug público) — senão `404 tenant_not_found`.
- calendário existe, pertence ao tenant, ativo — senão `404 calendar_not_found`.
- offering existe, pertence ao tenant, ativo **e atribuído ao calendário** — senão
  `404 offering_not_found`.
- Saída: `{"available_start_times": ["2027-07-01T12:00:00Z", ...]}` (ISO 8601 UTC com offset).

### 5.2 `CreateAppointmentUseCase` (público)

Entrada: `tenantSlug` + payload `{calendar_id, offering_id, start_at (com offset),
customer_name, customer_phone, customer_email?, customer_notes?}` + header opcional
`Idempotency-Key`.

Fluxo:

1. Valida tenant/calendário/offering/ativo/atribuição (mesmas regras do 5.1).
2. Canoniza o telefone (`phone_invalid` → 422).
3. Valida `start_at`: offset presente, boundary de 15 min, não passado, e serviço cabe na
   disponibilidade do calendário (regra 4.2) — violações → 422 `slot_unavailable`.
4. **Idempotência** (se header presente): busca por `(tenant_id, idempotency_key)`;
   fingerprint igual → replay (200 com o appointment original); fingerprint diferente →
   409 `idempotency_key_conflict`.
5. Abre transação curta (sem chamadas externas):
   a. Busca customer por telefone canônico; se não existir, cria (corrida em `UNIQUE(phone)` →
      re-ler dentro da transação e seguir). Nome/e-mail submetidos para telefone existente são
      descartados no MVP.
   b. Cria appointment com `duration_minutes_snapshot` e `calendar_timezone_snapshot`.
   c. Insere todos os slots de 4.1 em `appointment_slots`.
   d. Violação de `UNIQUE(calendar_id, slot_start_at)` → rollback total →
      409 `slot_unavailable`.
   e. Commit; resposta materializada a partir do estado commitado.
6. Resposta `201`: appointment com `id`, `start_at`, `end_at`, `status`, dados do serviço,
   calendário e customer (sem `user_id`, sem tokens).

## 6. Erros

| Código | HTTP | Quando |
| ------ | ---- | ------ |
| `tenant_not_found` / `calendar_not_found` / `offering_not_found` | 404 | recurso inexistente/inativo/não atribuído no escopo |
| `phone_invalid` | 422 | telefone não canonizável |
| `validation_error` | 422 | payload inválido (details por campo) |
| `slot_unavailable` | 409 (banco) / 422 (validação prévia) | horário indisponível |
| `idempotency_key_conflict` | 409 | mesma chave, payload diferente |

## 7. Testes obrigatórios

Unit (domínio puro):

- Geração de slots: 30/45/60 min e 4h; start desalinhado falha; duração não múltipla de 15
  falha; timezone tratada com cuidado.
- Disponibilidade: serviço cabe exatamente no fim da janela; ultrapassa e é removido; sem regra
  no dia → vazio; ocupados removem candidatos; regras sobrepostas deduplicadas; passado removido
  (clock injetável); DST de transição.
- Canonização: formatos variados de entrada; inválidos rejeitados.

Use case (mocks de ports):

- Sucesso criando customer novo; sucesso reutilizando customer existente; offering inexistente/
  inativo/não atribuído; calendário de outro tenant; horário fora da disponibilidade; snapshot
  correto; replay de idempotência; conflito de idempotência.

Integração (Testcontainers PostgreSQL real):

- Duas tentativas simultâneas do mesmo slot: uma vence, outra recebe 409; banco mantém apenas
  um conjunto de slots.
- Dois appointments parcialmente sobrepostos no mesmo calendário: o segundo recebe 409.
- Mesmos horários em calendários diferentes do mesmo tenant: ambos vencem.
- Rollback total em conflito (nenhum slot nem appointment residuais).
- Corrida de criação de customer pelo mesmo telefone: um só customer persiste.

## 8. Observabilidade do fluxo (P0 — catálogo de eventos)

`appointment.booking_succeeded`, `appointment.booking_replayed`,
`appointment.booking_rejected` (validação), `appointment.booking_conflict` (concorrência) —
sempre com `tenant.id`, `calendar.id`, `appointment.id` como attributes (não labels). Sem
telefone/nome do customer em logs.

## 9. Critérios de aceite

- [ ] Constraint `UNIQUE(calendar_id, slot_start_at)` migrada e testada sob concorrência real.
- [ ] Disponibilidade calculada sem persistir slots livres.
- [ ] Booking síncrono em uma transação curta; 409 em conflito.
- [ ] Idempotência com replay e conflito por fingerprint.
- [ ] Customer global reutilizado por telefone canônico.
