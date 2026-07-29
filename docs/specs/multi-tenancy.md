# Spec — Multi-tenancy e autorização

Status: aprovada para implementação (emendada na task 00.5 — ver notas "Emenda 00.5")
ADRs relacionados: 0003, 0004, 0005, 0006, 0016, 0017
Origem: livedoc Moira checkpoint 37.12 (blueprint) + decisões Gnomon (2026-07-23)

> **Emenda 00.5 (ADR 0017):** toda coluna `updated_at` desta spec é mantida por trigger de
> banco (não apenas `default now()`); toda FK recebe índice na mesma migration; colunas de
> status/role usam `CHECK` com conjunto fechado. E-mail em `CITEXT` torna a unicidade
> case-insensitive (dívida Moira: contas duplicadas por case).

---

## 1. Objetivo

Garantir que cada tenant opere isolado: múltiplos calendários por tenant, cada calendário de um
colaborador, clientes agendando com um calendário do tenant, e nenhum vazamento de dados entre
tenants — com autorização baseada em membership local.

## 2. Escopo

- Modelo de tenancy (entidades e invariantes).
- Resolução de identidade (JWT Keycloak → usuário local).
- Matriz de autorização por role.
- Regras de isolamento e comportamento cross-tenant.
- Estratégia de testes de vazamento.

Fora de escopo: convites por e-mail, billing por tenant, limites de uso por plano, schema/db
por tenant.

## 3. Entidades e invariantes

### 3.1 `users` (projeção global)

| Campo | Tipo | Restrições |
| ----- | ---- | ---------- |
| id | UUID | PK, `gen_random_uuid()` |
| keycloak_sub | VARCHAR(255) | NOT NULL, UNIQUE |
| email | CITEXT | NOT NULL, UNIQUE |
| display_name | VARCHAR(120) | NOT NULL |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL, default now() |

Invariante: existe no máximo uma projeção por identidade do Keycloak (`keycloak_sub`).

### 3.2 `tenants`

| Campo | Tipo | Restrições |
| ----- | ---- | ---------- |
| id | UUID | PK |
| name | VARCHAR(120) | NOT NULL |
| slug | VARCHAR(80) | NOT NULL, UNIQUE, lowercase |
| timezone | VARCHAR(64) | NOT NULL, IANA válida |
| currency_code | CHAR(3) | NOT NULL, default 'BRL', ISO 4217 |
| status | VARCHAR(32) | NOT NULL, default 'active', IN ('active','suspended') |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

### 3.3 `tenant_memberships`

| Campo | Tipo | Restrições |
| ----- | ---- | ---------- |
| id | UUID | PK |
| tenant_id | UUID | NOT NULL, FK tenants.id |
| user_id | UUID | NOT NULL, FK users.id |
| role | VARCHAR(16) | NOT NULL, IN ('owner','admin','staff') |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

Constraints: `UNIQUE(tenant_id, user_id)`; índice `(user_id)` (FK — ADR 0017).
Invariantes: todo tenant tem pelo menos um owner (a última membership owner não pode ser
removida nem rebaixada); `staff` deve corresponder a um `collaborators.user_id` vinculado.

### 3.4 `collaborators`

| Campo | Tipo | Restrições |
| ----- | ---- | ---------- |
| id | UUID | PK |
| tenant_id | UUID | NOT NULL, FK tenants.id |
| user_id | UUID | NULL, FK users.id |
| display_name | VARCHAR(120) | NOT NULL |
| is_active | BOOLEAN | NOT NULL, default true |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

Constraints: `UNIQUE(tenant_id, user_id)` (parcial, quando `user_id IS NOT NULL`); índice
`(tenant_id)` (FK — ADR 0017).

### 3.5 `calendars`

| Campo | Tipo | Restrições |
| ----- | ---- | ---------- |
| id | UUID | PK |
| tenant_id | UUID | NOT NULL, FK tenants.id |
| collaborator_id | UUID | NOT NULL, FK collaborators.id |
| name | VARCHAR(120) | NOT NULL (default: display_name do colaborador) |
| timezone | VARCHAR(64) | NOT NULL (default: timezone do tenant no cadastro) |
| is_active | BOOLEAN | NOT NULL, default true |
| created_at / updated_at | TIMESTAMPTZ | NOT NULL |

Constraints: `UNIQUE(tenant_id, collaborator_id)` (1:1 no MVP); índice `(collaborator_id)`
(FK — ADR 0017).

## 4. Resolução de identidade (request pipeline)

1. Spring Security valida o JWT do realm (issuer, audience, expiração, assinatura RS256).
2. Filtro pós-autenticação faz **JIT provisioning**: upsert em `users` por `keycloak_sub`
   (atualiza `email`/`display_name` se mudaram).
3. Controllers administrativos recebem o usuário local resolvido.
4. O use case valida: membership do usuário para o `tenantSlug` do path + role exigida pela
   operação. Sem membership → `403` (recurso do tenant não é revelado como existente; usar
   `404` quando o tenant do path não existe para qualquer caller ou quando se prefere não
   revelar existência — regra: tenant inexistente → 404; tenant existente sem membership → 403).

## 5. Matriz de autorização

| Operação | owner | admin | staff | público |
| -------- | ----- | ----- | ----- | ------- |
| Criar tenant (bootstrap) | — (qualquer autenticado) | — | — | não |
| Editar tenant / suspender | sim | não | não | não |
| Gerenciar memberships (add/remove/change role) | sim | leitura | não | não |
| CRUD colaboradores | sim | sim | não | não |
| Vincular/desvincular user ↔ collaborator | sim | sim | não | não |
| Editar calendário (nome, timezone, ativo) | sim | sim | próprio | não |
| CRUD offerings | sim | sim | não | não |
| Atribuir offerings ↔ calendários | sim | sim | não | não |
| CRUD availability rules | sim | sim | próprio calendário | não |
| Listar appointments do tenant | sim | sim | próprio calendário | não |
| Cancel/complete/no_show appointment | sim | sim | próprio calendário | não |
| Listar customers do tenant | sim | sim | não | não |
| Booking / catálogo / slots | — | — | — | sim (sem auth) |

"Próprio calendário": join `tenant_memberships(user_id, role='staff')` →
`collaborators(user_id)` → `calendars(collaborator_id)`.

## 6. Regras de isolamento

- Toda query administrativa filtra por `tenant_id` derivado do path validado — nunca por
  parâmetro de query opcional.
- IDs de recursos (calendar_id, offering_id, appointment_id) recebidos em payloads/paths devem
  ser validados como pertencentes ao tenant do path antes do uso; em rotas administrativas,
  recurso de outro tenant → `403`. Rotas públicas continuam ocultando recursos fora do escopo
  com `404`.
- Customers são globais: endpoints administrativos de customer expõem apenas customers com
  appointments no tenant, e apenas campos globais mínimos (nome, telefone, e-mail).
- Cache Redis de leituras públicas é particionado por tenant/calendário na chave.

## 7. Erros

| Código | HTTP | Quando |
| ------ | ---- | ------ |
| `tenant_not_found` | 404 | tenant do path inexistente (ou recurso filho inexistente no escopo) |
| `membership_required` | 403 | usuário autenticado sem membership no tenant |
| `insufficient_role` | 403 | membership existe, role insuficiente |
| `staff_calendar_mismatch` | 403 | staff tentando operar fora do próprio calendário |
| `last_owner` | 409 | tentativa de remover/rebaixar o último owner |

### 7.1 Tradução determinística de constraints (ADR 0016 — Emenda 00.5)

| Constraint | Código | HTTP |
| ---------- | ------ | ---- |
| `UNIQUE(keycloak_sub)` (`users`) | retry de leitura no JIT provisioning (não vaza ao cliente) | — |
| `UNIQUE(email)` (`users`, CITEXT) | conflito de projeção → retry no JIT; nunca exposto em escrita de cliente | — |
| `UNIQUE(slug)` (`tenants`) | `tenant_slug_taken` | 409 |
| `UNIQUE(tenant_id, user_id)` (`tenant_memberships`) | `membership_exists` | 409 |
| `UNIQUE(tenant_id, user_id)` parcial (`collaborators`) | `collaborator_already_linked` | 409 |
| `UNIQUE(tenant_id, collaborator_id)` (`calendars`) | `calendar_exists` | 409 |
| `CHECK role IN ('owner','admin','staff')` | `validation_error` (`role`) | 422 |
| `CHECK status IN ('active','suspended')` (`tenants`) | `validation_error` (`status`) | 422 |
| FKs (`tenant_id`, `user_id`, `collaborator_id`) | `validation_error` ou 404 de escopo (IDs validados como pertencentes ao tenant antes da escrita) | 404 / 422 |

## 8. Testes obrigatórios

Integração (Testcontainers + Keycloak):

- Tenant A não lê/edita nada do tenant B (ofertas, calendários, regras, appointments,
  customers) — cada rota administrativa com pelo menos um caso cross-tenant.
- Staff só acessa o próprio calendário; 403 no calendário de outro colaborador do mesmo tenant.
- Bootstrap: primeiro request autenticado provisiona `users`; `POST /v1/tenants` cria tenant +
  membership owner.
- Último owner não pode ser removido/rebaixado (409 `last_owner`).
- Recurso de outro tenant referenciado por ID em rota administrativa → 403.

Unit:

- Resolução de role → permissões da matriz (tabela de casos).
- Invariantes de membership (último owner).

## 9. Critérios de aceite

- [ ] Entidades 3.1–3.5 migradas com constraints e índices.
- [ ] Pipeline de identidade (seção 4) funcionando com Keycloak real e de teste.
- [ ] Matriz de autorização implementada e coberta por testes.
- [ ] Suite de vazamento cross-tenant verde.
