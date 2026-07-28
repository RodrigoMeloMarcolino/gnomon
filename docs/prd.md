# PRD — Gnomon

Versão: `0.2.1` (emendas da task 00.5 + decisão do frontend, ADR 0018 + nomenclatura Sun Catcher/Moonlight)
Data: 2026-07-23
Status: aprovado para início da implementação

---

## 1. Visão geral

O **Sun Catcher** é um SaaS de agendamento **multi-tenant** para prestadores de serviço — salões,
barbearias, tatuadores, clínicas pequenas, profissionais autônomos e negócios com equipe — no
qual cada negócio (tenant) configura serviços, colaboradores e calendários, e compartilha um
link público para que clientes finais marquem horários com o mínimo de atrito possível.

> **Nomenclatura (2026-07-24)**: *Sun Catcher* é o produto; `gnomon` é este serviço de API;
> `umbra` é o frontend. Este PRD usa "Gnomon" como abreviação do serviço. Registro central de
> ativos: repo `ephemeris` (convenção em `docs/architecture/naming.md`).

O Gnomon é a **recriação do Moira em stack Java** (Spring Boot 4 + Keycloak), evoluindo o modelo
de provider único para um modelo de tenant com múltiplos calendários de colaboradores. O nome
vem do gnômon, a peça do relógio de sol que projeta a sombra para medir as horas.

Prioridades do produto (herdadas do livedoc do SaaS):

- baixa fricção para o cliente final;
- consistência de agenda;
- prevenção de double booking;
- simplicidade para o MVP;
- arquitetura preparada para crescimento;
- boa separação de domínio;
- backend robusto e testável.

---

## 2. Contexto e motivação

O Moira (Python/FastAPI) validou o fluxo central de agendamento: signup de provider, catálogo
público, disponibilidade dinâmica, guest booking com customer global por telefone canônico,
slots discretos de 15 minutos e prevenção de double booking via constraint no banco.

O Gnomon recria esse core em uma nova stack por decisão estratégica, aproveitando para resolver
desde o início dois pontos que o Moira tratava como evolução futura:

1. **Multi-tenancy explícito** (blueprint registrado no checkpoint 37.12 do livedoc do Moira):
   o tenant fica **acima** da agenda, sem substituir a entidade pública de agendamento.
2. **Múltiplos calendários por tenant**: cada calendário pertence a um colaborador, e o cliente
   final escolhe com qual calendário do tenant quer agendar.
3. **Autenticação delegada ao Keycloak**: login, registro, recuperação de senha, MFA e social
   login saem do escopo da aplicação.

As decisões de produto validadas no Moira são **preservadas** (ver seção 12, rastreabilidade).

---

## 3. Problema

Prestadores de serviço com equipe precisam de uma agenda online que:

- permita que cada colaborador tenha sua própria agenda (calendário) dentro do mesmo negócio;
- permita que o negócio ofereça um catálogo de serviços, com controle de quais colaboradores
  executam quais serviços;
- permita que clientes agendem sem criar conta, escolhendo serviço, colaborador/calendário e
  horário;
- impeça double booking mesmo sob concorrência;
- dê ao dono do negócio controle sobre membros, agendas e atendimentos.

---

## 4. Personas e atores

| Ator | Descrição | Autenticação |
| ---- | --------- | ------------ |
| **Owner** | Dono do negócio. Cria o tenant, gerencia membros, colaboradores, calendários, serviços e agenda. | Keycloak |
| **Admin** | Membro do tenant com poderes administrativos, exceto gestão de ownership. | Keycloak |
| **Staff** | Colaborador vinculado a uma conta de usuário. Gerencia a própria disponibilidade e acompanha a própria agenda. | Keycloak |
| **Colaborador (sem login)** | Entidade de agenda gerenciada pelo owner/admin, sem conta na plataforma. | nenhuma |
| **Customer** | Cliente final. Agenda sem conta (guest booking), informando nome, telefone/WhatsApp e e-mail opcional. | nenhuma |

Roles de plataforma futuras (super-admin interno) ficam fora do MVP.

---

## 5. Modelo de domínio multi-tenant

```
Keycloak (realm "gnomon")
   │  identidade/autenticação
   ▼
users (projeção local: keycloak_sub, email, display_name)
   │
   └──N:M── tenants  ◀── tenant_memberships (role: owner | admin | staff)
                │
                ├──1:N── collaborators (user_id nullable — login opcional)
                │            │
                │            └──1:1── calendars ──1:N── availability_rules
                │                          │
                ├──1:N── offerings         ├──1:N── appointments ──1:N── appointment_slots
                │            │             │           UNIQUE(calendar_id, slot_start_at)
                └────────────┴── calendar_offerings ◄──┘
                                                        │
                                   customers (globais, phone canônico único) ◄──N:1── appointments
```

Regras estruturais:

- **Tenant** é a conta/negócio. Toda tabela tenant-owned carrega `tenant_id`, mesmo quando
  `calendar_id` continuar existindo por regra de negócio.
- **Calendar** é o dono da agenda: disponibilidade semanal, appointments e o lock de concorrência
  (`UNIQUE(calendar_id, slot_start_at)`) são por calendário.
- No MVP, a relação collaborator ↔ calendar é **1:1** (todo colaborador tem exatamente um
  calendário; o calendário nasce junto com o colaborador). N calendários por colaborador é
  evolução futura e não exige mudança estrutural.
- **Offering** é do tenant; `calendar_offerings` define quais colaboradores executam quais
  serviços. O booking exige que o serviço esteja atribuído ao calendário escolhido.
- **Customer** permanece **global**, reutilizado por telefone canônico, sem `tenant_id` direto.
  A relação customer ↔ tenant é inferida pelos appointments. Dados de CRM por tenant ficam em
  tabela relacional futura (`tenant_customers`).
- `tenants.timezone` é o default; `calendars.timezone` pode sobrescrever (colaborador em outra
  região). `tenants.currency_code` governa os preços dos offerings (ISO 4217, default `BRL`).

---

## 6. Requisitos funcionais

### 6.1 Identidade e tenants

- **RF-01** Registro, login, logout, recuperação de senha e (futuro) MFA são delegados ao
  Keycloak. A API nunca recebe nem armazena senha.
- **RF-02** No primeiro request autenticado, a API provisiona a projeção local do usuário
  (`users`) a partir das claims do token (JIT provisioning).
- **RF-03** `POST /v1/tenants` cria o tenant e a membership `owner` do usuário autenticado
  (bootstrap). Slug do tenant é único global.
- **RF-04** Owner pode adicionar usuários existentes ao tenant com role `admin`. A role `staff`
  só nasce ou permanece quando o usuário é vinculado a um colaborador (RF-07); criação direta de
  `staff` sem colaborador é rejeitada. Convite por e-mail é evolução futura.
- **RF-05** Um usuário pode pertencer a múltiplos tenants; rotas administrativas carregam o
  tenant no path (`/v1/tenants/{tenantSlug}/...`) e a API valida membership + role.

### 6.2 Colaboradores, calendários e catálogo

- **RF-06** Owner/admin cadastra colaboradores (nome obrigatório; bio/foto futuras). Cada
  colaborador criado recebe automaticamente seu calendário (1:1).
- **RF-07** Owner/admin pode vincular um colaborador a um usuário da plataforma (login
  opcional), concedendo acesso `staff` à própria agenda.
- **RF-08** Owner/admin cadastra offerings do tenant: título, descrição, duração em minutos
  (múltiplo positivo de 15), preço opcional em centavos, ativo/inativo.
- **RF-09** Owner/admin atribui offerings a calendários (`calendar_offerings`). Serviço não
  atribuído não pode ser agendado naquele calendário.
- **RF-10** Owner/admin (ou staff para o próprio calendário) cadastra regras de disponibilidade
  semanal do calendário: `weekday` (1=segunda … 7=domingo), `start_time`, `end_time`.

### 6.3 Booking público (guest booking)

- **RF-11** Rotas públicas não exigem autenticação: perfil do tenant, lista de calendários,
  catálogo, horários disponíveis e criação de appointment.
- **RF-12** `available-slots` calcula disponibilidade dinamicamente: regras semanais do
  calendário + duração do serviço + slots ocupados, na timezone do calendário, retornando
  instantes UTC. Slots disponíveis nunca são persistidos.
- **RF-13** `POST .../appointments` cria o agendamento em uma única requisição síncrona:
  valida tenant/calendário/offering (ativo e atribuído ao calendário), valida e canoniza o
  telefone, busca ou cria o customer global, calcula `end_at` a partir do snapshot de duração,
  valida aderência à disponibilidade e insere appointment + slots ocupados em uma única
  transação curta.
- **RF-14** Violação de `UNIQUE(calendar_id, slot_start_at)` faz rollback total e retorna
  `409 Conflict` com código de horário indisponível.
- **RF-15** Booking público **exige** header `Idempotency-Key` (ADR 0014, emenda da task 00.5;
  ausência → 422): mesma chave + mesmo payload → replay do appointment original; mesma chave +
  payload diferente → conflito. Unicidade por tenant:
  `UNIQUE(tenant_id, idempotency_key)`.
- **RF-16** O appointment grava `duration_minutes_snapshot` e a timezone do calendário no
  momento da criação (histórico estável contra edições futuras).

### 6.4 Painel administrativo

- **RF-17** Owner/admin lista appointments do tenant com filtros por data, calendário e status.
  Staff lista apenas appointments do próprio calendário.
- **RF-18** Owner/admin/staff (no próprio calendário) pode cancelar, marcar `completed` ou
  `no_show` em appointments `scheduled`.
- **RF-19** Owner/admin consulta customers relacionados ao tenant (inferidos via appointments).

### 6.5 Cancelamento e remarcação pelo cliente (pós-MVP-core)

- **RF-20** Cancelamento público via token seguro por appointment (hash armazenado), liberando
  os slots na mesma transação e mantendo o histórico como `cancelled`.
- **RF-21** Remarcação pública via token: mesma estratégia transacional da criação; conflito
  nos novos slots mantém o appointment anterior intacto.

---

## 7. Requisitos não funcionais

- **RNF-01** Consistência de agenda garantida pelo banco (constraint única + transação curta),
  nunca apenas por validação de leitura. Leitura de disponibilidade é advisory.
- **RNF-02** Sem chamadas externas dentro da transação de booking.
- **RNF-03** Isolamento multi-tenant: toda consulta administrativa é tenant-scoped; testes de
  integração devem cobrir explicitamente vazamento cross-tenant.
- **RNF-04** Instantes reais em `TIMESTAMPTZ`; regras semanais em horário local do calendário;
  conversões sempre explícitas via timezone IANA.
- **RNF-05** Dinheiro como inteiro em minor units; proibido float.
- **RNF-06** Observabilidade vendor-neutral: logs JSON em stdout (obrigatório) + OTLP opcional;
  sem PII, credenciais ou chaves de idempotência em logs.
- **RNF-07** Cache Redis apenas para leituras públicas, fail-open (indisponibilidade do Redis
  não derruba a API); booking nunca confia em cache.
- **RNF-08** Segredos via variáveis de ambiente; configuração por ambiente (`local`, `test`,
  `prod`); nada de secrets commitados.
- **RNF-09** API versionada sob `/v1` com envelope de erro estável.
- **RNF-10** Stack alvo: Java 21, Spring Boot 4, PostgreSQL 16+, Keycloak 26+, Redis 7+.

---

## 8. Escopo do MVP

### Dentro

- Fundação técnica (Spring Boot 4, Flyway, Docker Compose com PostgreSQL/Redis/Keycloak).
- Identidade via Keycloak + memberships + bootstrap de tenant.
- Colaboradores, calendários, offerings, atribuição e availability rules.
- Cálculo dinâmico de disponibilidade e guest booking com concorrência segura.
- Painel administrativo básico (listar/filtrar appointments, cancel/complete/no_show).
- Cache Redis das leituras públicas.
- Observabilidade (logs estruturados + OTLP).
- CI com lint, unit tests, integration tests (Testcontainers) e approval gate.

### Fora (evoluções futuras)

- Cancelamento/remarcação pública via token (RF-20/RF-21 — primeiro item pós-MVP-core).
- Confirmação de telefone via SMS/WhatsApp.
- Notificações (e-mail/WhatsApp) — provedor indefinido.
- Reserva temporária com TTL (`booking_reservations`).
- Múltiplos calendários por colaborador.
- Convites por e-mail para membros do tenant.
- Marketplace, pagamentos, billing, planos.
- Camada de agente/IA, WhatsApp como canal, RAG por tenant (ver roadmap do livedoc original).
- Frontend: decisão fechada (ADR 0018) — repo separado `umbra`, fora do escopo deste repo.
- **Moonlight** (2026-07-24): produto futuro **cross-tenant** de métricas + integrações de
  marketing — serviço separado consumindo eventos de domínio (nunca endpoints admin relaxados
  nem analytics no OLTP). Ficha no repo `ephemeris` (`docs/products/moonlight.md`); semente:
  envelope de eventos da fase 06.

---

## 9. Contratos de API (resumo)

### Públicos (sem autenticação)

| Método | Rota | Descrição |
| ------ | ---- | --------- |
| GET | `/v1/public/tenants/{slug}` | Perfil público do tenant |
| GET | `/v1/public/tenants/{slug}/calendars` | Calendários (colaboradores) ativos |
| GET | `/v1/public/tenants/{slug}/offerings` | Catálogo de serviços do tenant |
| GET | `/v1/public/tenants/{slug}/available-slots?calendar_id=&offering_id=&date=` | Horários disponíveis (UTC) |
| POST | `/v1/public/tenants/{slug}/appointments` | Cria appointment (exige `Idempotency-Key`) |

### Administrativos (Bearer JWT do Keycloak + membership)

| Método | Rota | Role mínima |
| ------ | ---- | ----------- |
| POST | `/v1/tenants` | autenticado (bootstrap) |
| GET | `/v1/tenants` | autenticado (meus tenants) |
| GET/PATCH | `/v1/tenants/{tenantSlug}` | owner/admin |
| GET/POST/DELETE | `/v1/tenants/{tenantSlug}/memberships[...]` | owner (admin: leitura) |
| POST/GET/PATCH | `/v1/tenants/{tenantSlug}/collaborators[...]` | owner/admin |
| GET/PATCH | `/v1/tenants/{tenantSlug}/calendars/{calendarId}` | owner/admin/staff (próprio) |
| POST/GET/PATCH/DELETE | `/v1/tenants/{tenantSlug}/offerings[...]` | owner/admin |
| PUT | `/v1/tenants/{tenantSlug}/calendars/{calendarId}/offerings` | owner/admin (atribuição) |
| POST/GET/PATCH/DELETE | `/v1/tenants/{tenantSlug}/calendars/{calendarId}/availability-rules[...]` | owner/admin/staff (próprio) |
| GET | `/v1/tenants/{tenantSlug}/appointments` | owner/admin; staff: próprio calendário |
| POST | `/v1/tenants/{tenantSlug}/appointments/{id}/cancel|complete|no-show` | owner/admin/staff (próprio) |
| GET | `/v1/tenants/{tenantSlug}/customers[...]` | owner/admin |

Erros seguem o envelope `{"error": {"code", "message", "details"}}`.

---

## 10. Modelo de dados (resumo)

Tabelas: `users`, `tenants`, `tenant_memberships`, `collaborators`, `calendars`, `offerings`,
`calendar_offerings`, `availability_rules`, `customers`, `appointments`, `appointment_slots`.

Pontos críticos:

- `users.keycloak_sub` único (vínculo com o subject do token); `users.email` único (CITEXT).
- `tenants.slug` único, lowercase; `tenants.currency_code CHAR(3)` default `BRL`;
  `tenants.timezone` IANA obrigatória.
- `tenant_memberships`: `UNIQUE(tenant_id, user_id)`, `role IN ('owner','admin','staff')`.
- `collaborators`: `UNIQUE(tenant_id, user_id)` quando `user_id` presente (um usuário = no
  máximo um colaborador por tenant no MVP).
- `calendars`: `UNIQUE(tenant_id, collaborator_id)` (1:1 no MVP), `timezone` com default do
  tenant, `is_active`.
- `offerings`: `duration_minutes > 0 AND duration_minutes % 15 = 0`;
  `price_cents IS NULL OR price_cents >= 0`; `UNIQUE(tenant_id, lower(title)) WHERE is_active`.
- `availability_rules`: `calendar_id`, `weekday BETWEEN 1 AND 7`, `start_time < end_time`,
  índice `(calendar_id, weekday)`.
- `customers`: `UNIQUE(phone)` canônico global, `email` opcional, `phone_verified_at` futuro.
- `appointments`: `tenant_id`, `calendar_id`, `offering_id`, `customer_id`, `start_at < end_at`,
  `duration_minutes_snapshot` (>0, %15), `calendar_timezone_snapshot`,
  `status IN ('scheduled','cancelled','completed','no_show')`,
  `UNIQUE(tenant_id, idempotency_key)`, hashes de tokens futuros.
- `appointment_slots`: `UNIQUE(calendar_id, slot_start_at)` — defesa final contra double booking.

Extensões PostgreSQL: `pgcrypto`, `citext`.

---

## 11. Fluxos críticos

### 11.1 Bootstrap do tenant

1. Usuário se registra/faz login no Keycloak (frontend) e obtém access token.
2. Primeira chamada autenticada → JIT provisioning da projeção local `users`.
3. `POST /v1/tenants` com nome/slug/timezone → cria tenant + membership `owner`.
4. Owner cadastra colaborador → calendário criado junto (1:1).
5. Owner cadastra offerings e atribui aos calendários.
6. Owner/staff configura availability rules do calendário.

### 11.2 Guest booking

1. Customer abre o link público do tenant, escolhe calendário (colaborador) e serviço.
2. Frontend consulta `available-slots` (cálculo dinâmico, advisory).
3. Customer escolhe horário e informa nome + telefone (+ e-mail opcional).
4. Uma única requisição síncrona cria customer (ou reaproveita por telefone canônico),
   appointment e slots, na mesma transação.
5. Conflito de constraint → rollback + `409`; sucesso → confirmação com dados do appointment.

### 11.3 Concorrência

Dois clientes podem ver o mesmo horário disponível; o primeiro commit vence, o segundo recebe
`409` e escolhe outro horário. Não há reserva temporária no MVP.

---

## 12. Rastreabilidade com o Moira

| Decisão Moira (ADR/livedoc) | Destino no Gnomon |
| --------------------------- | ----------------- |
| 0001 Stack Python/FastAPI | **Substituída** por ADR 0001 (Java 21 + Spring Boot 4). Restante da stack preservado. |
| 0002 Guest booking | Preservada (ADR 0008). |
| 0003 Customer ≠ User | Preservada (ADR 0009). User agora é projeção do Keycloak. |
| 0004 Customer global por telefone canônico | Preservada (ADR 0009). |
| 0005/0006 Slots 15 min / não persistir disponíveis | Preservadas (ADR 0010). |
| 0007 Double booking via constraint | Preservada, escopo sobe para calendário (ADR 0011). |
| 0008/0009 Snapshot de duração / múltiplo de 15 | Preservadas (ADR 0012). |
| 0010 Arquitetura modular pragmática | Preservada, package by feature (ADR 0002). |
| 0011 Moeda do provider + price_cents | Preservada, moeda sobe para o tenant (ADR 0013). |
| 0012 Política de senha | **Substituída**: credenciais saem da aplicação com o Keycloak (ADR 0004). |
| 0013 Auth JWT HS256 + ownership + idempotência | **Substituída** a parte de auth (Keycloak, ADR 0004); ownership vira membership/roles (ADR 0003/0004); idempotência preservada (ADR 0014). |
| 0014 Rotas `/v1` + envelope de erro | Preservada (ADR 0014). |
| 0015 Logging vendor-neutral + OTLP | Preservada (ADR 0015). |
| Checkpoint 37.12 Blueprint multi-tenancy | **Implementado** como fundação (ADRs 0003–0007). |

Decisões novas sem equivalente no Moira: ADR 0005 (colaborador entidade + login opcional),
ADR 0006 (calendário dono da agenda), ADR 0007 (serviços no tenant com atribuição),
ADR 0016 (validação simétrica + tradução determinística de constraints) e ADR 0017
(disciplina de schema/DDL) — as duas últimas nascidas da auditoria de dívidas do Moira
(task 00.5).

---

## 13. Decisões em aberto

1. ~~**Frontend**: React, Next.js ou outro — indefinido.~~ **Fechada (2026-07-23, ADR 0018)**:
   frontend no repo separado `umbra` — Next.js 16 + React 19 + TypeScript, Tailwind v4 +
   shadcn/ui, calendário próprio, OIDC via Keycloak; booking público em `/t/{slug}` e admin em
   `/app/{slug}`. Follow-up para o backend: CORS para o dev server na fase 01.
2. **Notificações**: provedor de e-mail/WhatsApp/SMS — indefinido.
3. **Slug público por calendário**: link direto para um colaborador
   (`/t/{tenant}/{calendario}`) — provável sim, a detalhar na fase 02.
4. **Versão do Java**: 21 LTS assumido; 25 LTS é opção trivial de configuração.
5. **Sincronização do livedoc original** com a decisão Gnomon/Java — follow-up manual.
6. **Antecedência mínima de booking**: o Moira permitia reservar slot a 1 minuto do início;
   política de lead time mínimo (por tenant? por offering?) permanece em aberto — sem regra no
   MVP (task 00.5).

---

## 14. Roadmap

Ver `docs/tasks/README.md` para o detalhamento. Fases:

0. Fundação técnica
0.5. Hardening de nascimento: lições do Moira (docs-only)
1. Identidade (Keycloak) e tenancy
2. Catálogo (colaboradores, calendários, offerings, atribuição)
3. Disponibilidade
4. Guest booking
5. Cache Redis
6. Observabilidade
7. Painel administrativo
8. Cancelamento e remarcação via token
9. Gates de CI e hardening
