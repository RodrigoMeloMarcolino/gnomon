# Spec — Core de booking (slots, disponibilidade, criação transacional)

Status: aprovada para implementação (emendada na task 00.5 — ver notas "Emenda 00.5")
ADRs relacionados: 0006, 0007, 0008, 0009, 0010, 0011, 0012, 0014, 0016, 0017
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

PK composta `(calendar_id, offering_id)`; ambas FKs. Índice `(offering_id)` (lado da FK não
coberto pelo prefixo da PK — ADR 0017).

### `availability_rules`

`id`, `calendar_id` (FK), `weekday` SMALLINT (1–7, 1=segunda), `start_time` < `end_time`
(TIME local), `is_active` (default true), timestamps. Índice `(calendar_id, weekday)`.
Constraints: `CHECK (weekday BETWEEN 1 AND 7)`, `CHECK (start_time < end_time)` e
**`CHECK` de alinhamento de 15 min** em `start_time`/`end_time` (minuto ∈ {0,15,30,45},
segundos zerados). A mesma validação de alinhamento ocorre na escrita (bean validation +
domínio) — validação simétrica (ADR 0016): nenhuma regra que quebraria o cálculo de
disponibilidade pode ser persistida.

### `customers`

`id`, `name` (120), `phone` (32, UNIQUE — canônico E.164), `email` (CITEXT, null), timestamps.
(Emenda 00.5: `phone_verified_at` removida — confirmação de telefone está fora do MVP e a
coluna nascerá na migration da fase que a implementar; ADR 0017, zero colunas mortas.)

### `appointments`

`id`, `tenant_id` (FK), `calendar_id` (FK), `offering_id` (FK), `customer_id` (FK),
`start_at` < `end_at` (TIMESTAMPTZ), `duration_minutes_snapshot` (>0, %15=0),
`calendar_timezone_snapshot` (IANA), `status` (default 'scheduled', CHECK IN
('scheduled','cancelled','completed','no_show')), `customer_notes` (null),
`idempotency_key` (NOT NULL), `idempotency_fingerprint` (NOT NULL), timestamps.
Constraints: `UNIQUE(tenant_id, idempotency_key)`.
Índices: `(calendar_id, start_at)`, `(tenant_id, status, start_at)`, `(customer_id)`,
`(offering_id)`.
Máquina de estados de `status`: `scheduled` → `cancelled` | `completed` | `no_show`;
`cancelled`/`completed`/`no_show` são terminais (nenhuma transição de saída). Transições são
exclusividade dos use cases admin (fase 07) e públicos por token (fase 08).
(Emenda 00.5: `cancel_token_hash`/`reschedule_token_hash` removidas — nascem em migration
aditiva da fase 08, que é a fase que as utiliza; ADR 0017, zero colunas mortas.)

### `appointment_slots`

`id`, `appointment_id` (FK), `calendar_id` (FK), `slot_start_at` (TIMESTAMPTZ).
Constraint crítica: **`UNIQUE(calendar_id, slot_start_at)`**. Índice `(appointment_id)`
(FK — ADR 0017). Append-only: sem `updated_at`.

> Nota geral (Emenda 00.5): "timestamps" nesta spec significa `created_at` + `updated_at`
> mantidos por trigger de banco (ADR 0017). Toda constraint nomeada acima tem tradução na
> tabela da seção 6.1 (ADR 0016).

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
customer_name, customer_phone, customer_email?, customer_notes?}` + header **obrigatório**
`Idempotency-Key` (Emenda 00.5 / ADR 0014: ausência → 422 `validation_error`).

Fluxo:

1. Valida tenant/calendário/offering/ativo/atribuição (mesmas regras do 5.1).
2. Canoniza o telefone (`phone_invalid` → 422).
3. Valida `start_at`: offset presente, boundary de 15 min, não passado, e serviço cabe na
   disponibilidade do calendário (regra 4.2) — violações → 422 `slot_unavailable`.
   **Segundos/nanos não-zero são rejeitados (422 `validation_error`), nunca truncados
   silenciosamente** (Emenda 00.5 / ADR 0016: no Moira o truncamento silencioso de `09:00:45`
   para `09:00` era surpresa de contrato).
4. **Idempotência**: busca por `(tenant_id, idempotency_key)`; fingerprint igual → replay
   (200 com o appointment original); fingerprint diferente → 409 `idempotency_key_conflict`.
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

### 6.1 Tradução determinística de constraints (ADR 0016 — Emenda 00.5)

Toda constraint nomeada da seção 3 tem tradução explícita; violação de integridade conhecida
nunca vira 500:

| Constraint | Código | HTTP |
| ---------- | ------ | ---- |
| `UNIQUE(calendar_id, slot_start_at)` (`appointment_slots`) | `slot_unavailable` | 409 |
| `UNIQUE(tenant_id, idempotency_key)` (`appointments`) | replay ou `idempotency_key_conflict` (conforme fingerprint) | 200 / 409 |
| `UNIQUE(phone)` (`customers`) | retry de leitura dentro da transação (não vaza ao cliente) | — |
| `UNIQUE(tenant_id, lower(title)) WHERE is_active` (`offerings`) | `validation_error` (`title`) | 422 |
| `CHECK (duration_minutes > 0 AND % 15 = 0)` (`offerings`) | `validation_error` (`duration_minutes`) | 422 |
| `CHECK (price_cents >= 0)` (`offerings`) | `validation_error` (`price_cents`) | 422 |
| `CHECK (weekday BETWEEN 1 AND 7)` (`availability_rules`) | `validation_error` (`weekday`) | 422 |
| `CHECK (start_time < end_time)` (`availability_rules`) | `validation_error` (`start_time`/`end_time`) | 422 |
| `CHECK` alinhamento 15 min (`availability_rules`) | `validation_error` (`start_time`/`end_time`) | 422 |
| `CHECK (start_at < end_at)` (`appointments`) | `validation_error` (`start_at`) | 422 |
| `CHECK` snapshot duração (`appointments`) | `validation_error` (`duration_minutes`) | 422 |
| `CHECK status IN (...)` (`appointments`) | `validation_error` (`status`) | 422 |
| FKs (`calendar_offerings`, `appointments`, `appointment_slots`) | `validation_error` ou 404 de escopo (IDs são validados como pertencentes ao tenant antes da escrita) | 404 / 422 |

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
- **Corrida com a mesma `Idempotency-Key` e mesmo payload: ambas as requests obtêm o mesmo
  appointment (um cria, outro faz replay); apenas um appointment persiste** (Emenda 00.5 — no
  Moira esse caminho só tinha cobertura sequencial).
- Escrita de `availability_rules` com horário desalinhado (ex.: `09:07`) é rejeitada na borda
  (422) e por CHECK — e não derruba o endpoint público de slots (regressão da dívida Moira,
  ADR 0016).

## 8. Observabilidade do fluxo (P0 — catálogo de eventos)

`appointment.booking_succeeded`, `appointment.booking_replayed`,
`appointment.booking_rejected` (validação), `appointment.booking_conflict` (concorrência) —
sempre com `tenant.id`, `calendar.id`, `appointment.id` como attributes (não labels). Sem
telefone/nome do customer em logs.

## 9. Critérios de aceite

- [ ] Constraint `UNIQUE(calendar_id, slot_start_at)` migrada e testada sob concorrência real.
- [ ] Disponibilidade calculada sem persistir slots livres.
- [ ] Booking síncrono em uma transação curta; 409 em conflito.
- [ ] Idempotência com replay e conflito por fingerprint; `Idempotency-Key` obrigatório.
- [ ] Customer global reutilizado por telefone canônico.
- [ ] Tabela 6.1 implementada: nenhuma violação de constraint conhecida retorna 500.
- [ ] Escrita de `availability_rules` rejeita horários desalinhados (validação simétrica).
- [ ] `start_at` com segundos não-zero rejeitado (422), sem truncamento.
- [ ] Máquina de estados de `status` com CHECK e transições documentadas.
