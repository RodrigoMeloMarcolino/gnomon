# PRD — Gnomon Node.js

Versão: `0.1.0`
Data: 2026-08-19
Status: aprovado para implementação; TypeORM aceito pelo ADR 0002

## 1. Visão geral

O **Sun Catcher** é um SaaS de agendamento multi-tenant para prestadores de serviço. Cada
negócio configura colaboradores, calendários, serviços e disponibilidade; customers agendam
sem conta; owner, admin e staff operam o painel autenticado pelo Umbra.

Este repositório é o novo backend de produção `gnomon`, em Node.js e TypeScript. O backend Java
anterior é preservado como `gnomon-mock`, referência executável do contrato `/v1` durante a
transição.

## 2. Objetivos

- Reimplementar o domínio existente sem interromper o desenvolvimento do Umbra.
- Manter compatibilidade observável com a API `/v1` do mock.
- Preservar isolamento multi-tenant, idempotência e consistência concorrente.
- Simplificar booking usando uma linha por appointment e exclusion constraint GiST.
- Manter arquitetura modular, domínio testável e infraestrutura substituível.
- Permitir evolução posterior do contrato sob governança do novo Gnomon.

## 3. Fora de escopo da transição inicial

- Compatibilidade binária, de classes ou de schema com o backend Java.
- Compartilhar banco ou executar dual write com o mock.
- Corrigir contratos incompatíveis sem versão nova ou decisão explícita.
- Implementar portfólio, notificações, pagamentos ou IA junto da paridade inicial.
- Reproduzir bugs incidentais não documentados apenas porque o mock os apresenta.

## 4. Atores

| Ator                  | Responsabilidade                            | Autenticação |
| --------------------- | ------------------------------------------- | ------------ |
| Owner                 | Gerencia tenant, membros, catálogo e agenda | Keycloak     |
| Admin                 | Gerencia operação, sem ownership            | Keycloak     |
| Staff                 | Opera o próprio calendário                  | Keycloak     |
| Colaborador sem login | Entidade de agenda administrada pelo tenant | nenhuma      |
| Customer              | Agenda como guest                           | nenhuma      |

## 5. Modelo de domínio

```text
Keycloak
  └─ users ── tenant_memberships ── tenants
                                  ├─ collaborators ── calendars ── availability_rules
                                  ├─ offerings ────── calendar_offerings
                                  └─ appointments ─── customers (globais)
                                         └─ EXCLUDE tenant/calendar/tstzrange
```

- Tenant é a conta; calendar é o dono da agenda.
- Collaborator e calendar são 1:1 no MVP.
- Offering pertence ao tenant e precisa estar atribuído ao calendar.
- Customer é global e reutilizado pelo telefone canônico.
- Appointment é histórico e também participa da garantia concorrente.
- Não existe tabela `appointment_slots`.

## 6. Requisitos funcionais

### 6.1 Identidade e tenancy

- **RF-01** Validar JWT RS256 do Keycloak, incluindo issuer, audience e expiração.
- **RF-02** Provisionar projeção local do usuário no primeiro request autenticado.
- **RF-03** Criar tenant e membership owner atomicamente.
- **RF-04** Permitir múltiplos tenants por usuário, com tenant explícito no path.
- **RF-05** Resolver autorização local por membership `owner | admin | staff`.
- **RF-06** Negar acesso cross-tenant administrativo com `403`; recursos públicos inexistentes
  retornam `404`.

### 6.2 Catálogo e disponibilidade

- **RF-07** Owner/admin gerencia collaborators; o calendar 1:1 nasce na mesma transação.
- **RF-08** Vínculo opcional de collaborator com User concede acesso staff ao próprio calendar.
- **RF-09** Owner/admin gerencia offerings com duração positiva múltipla de 15 e preço em
  centavos.
- **RF-10** Owner/admin atribui offerings a calendars.
- **RF-11** Owner/admin e staff autorizado gerenciam availability rules alinhadas a 15 minutos.
- **RF-12** Calcular horários dinamicamente a partir de regras, timezone, duração e appointments
  `scheduled`; disponibilidade nunca é persistida.
- **RF-13** DST gap produz zero instante e DST overlap produz os dois instantes UTC válidos.

### 6.3 Booking público

- **RF-14** Expor perfil, calendars, offerings, slots e criação de appointment sem autenticação.
- **RF-15** Exigir `Idempotency-Key` como UUID canônico minúsculo.
- **RF-16** Normalizar customer e telefone, reutilizando customer global de forma concorrente.
- **RF-17** Na mesma transação curta: validar contexto, resolver idempotência, criar/reutilizar
  customer e inserir appointment.
- **RF-18** Impedir overlap pela constraint
  `ex_appointments_tenant_calendar_period` usando intervalo `[start_at,end_at)` somente para
  status `scheduled`.
- **RF-19** Mesma chave e payload retorna replay `200`; chave igual e payload diferente retorna
  `409 idempotency_key_conflict`; overlap concorrente retorna `409 slot_unavailable`.
- **RF-20** Guardar snapshots de duração e timezone no appointment.
- **RF-21** Invalidar cache e publicar eventos somente depois do commit.

### 6.4 Administração

- **RF-22** Owner/admin lista appointments do tenant; staff somente do próprio calendar.
- **RF-23** Cancelamento muda status e libera imediatamente o intervalo GiST.
- **RF-24** `completed` e `no_show` só podem ser aplicados depois de `end_at`.
- **RF-25** Remarcação futura atualiza o intervalo atomicamente; conflito restaura estado e
  tokens pelo rollback.
- **RF-26** Owner/admin consulta customers relacionados ao tenant por appointments.
- **RF-27** Uma política operacional deve tratar appointments `scheduled` vencidos.

## 7. Requisitos de compatibilidade `/v1`

- **RC-01** Preservar métodos, paths, query params e headers consumidos pelo Umbra.
- **RC-02** Preservar schemas, nullability e casing publicado, inclusive a convivência de
  `snake_case` público e contratos administrativos legados.
- **RC-03** Preservar status HTTP e envelope
  `{"error":{"code","message","details"}}`.
- **RC-04** Preservar Keycloak, CORS, callbacks e audience observáveis pelo frontend.
- **RC-05** Publicar OpenAPI JSON/YAML e bloquear breaking changes em CI.
- **RC-06** Executar a mesma suíte black-box contra `gnomon-mock` e `gnomon`.
- **RC-07** Divergências precisam ser classificadas como regra normativa, dependência do Umbra,
  bug compatível ou mudança que exige `/v2`.

## 8. Requisitos não funcionais

- **RNF-01** PostgreSQL é a garantia final de concorrência; checks prévios são advisory.
- **RNF-02** Não executar chamadas externas dentro da transação de booking.
- **RNF-03** Toda consulta administrativa é tenant-scoped e coberta contra vazamento.
- **RNF-04** Usar `TIMESTAMPTZ`, timezone IANA e biblioteca temporal testada; não fazer lógica
  local apenas com `Date`.
- **RNF-05** Dinheiro é inteiro em minor units.
- **RNF-06** Redis é cache-aside apenas para leituras públicas e falha de forma aberta.
- **RNF-07** Logs JSON em stdout e OTLP opcional/fail-open, sem PII ou segredos.
- **RNF-08** Processamento de imagens e outras cargas CPU-bound executam em worker separado.
- **RNF-09** Segredos vêm do ambiente; nenhuma credencial é commitada.
- **RNF-10** Migrations são versionadas, revisadas e aplicadas por um único runner.
- **RNF-11** Sincronização automática de schema pelo ORM é proibida.
- **RNF-12** Testes de persistência, concorrência, Redis e Keycloak usam serviços reais via
  Testcontainers.
- **RNF-13** Arquitetura `api → application → domain ← infrastructure` é verificada em CI.

## 9. Contrato HTTP inicial

O baseline completo será extraído do OpenAPI do `gnomon-mock`. O conjunto mínimo contém:

- `GET /v1/public/tenants/{slug}`;
- `GET /v1/public/tenants/{slug}/calendars`;
- `GET /v1/public/tenants/{slug}/offerings`;
- `GET /v1/public/tenants/{slug}/available-slots`;
- `POST /v1/public/tenants/{slug}/appointments`;
- tenants, memberships, collaborators, calendars, offerings e availability rules admin;
- appointments e customers admin;
- `/v1/health`, `/v1/ready`, `/v3/api-docs` e `/v3/api-docs.yaml`.

## 10. Persistência e booking GiST

Tabelas iniciais: `users`, `tenants`, `tenant_memberships`, `collaborators`, `calendars`,
`offerings`, `calendar_offerings`, `availability_rules`, `customers` e `appointments`.

Forma alvo da garantia concorrente:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
ADD CONSTRAINT ex_appointments_tenant_calendar_period
EXCLUDE USING gist (
    tenant_id WITH =,
    calendar_id WITH =,
    tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (status = 'scheduled');
```

A unique `(tenant_id, idempotency_key)` permanece separada. O GiST não substitui índices B-tree
de listagem e ordenação.

## 11. Estratégia de entrega

1. Congelar OpenAPI e cenários do mock.
2. Fechar ADR 0002 com spike TypeORM × Prisma.
3. Implementar fundação, segurança, erro e observabilidade mínima.
4. Implementar booking GiST como primeiro slice de alto risco.
5. Portar tenancy, catálogo e disponibilidade por fatias verticais.
6. Completar administração e cache.
7. Rodar contract tests contra mock e sucessor.
8. Fazer staging, shadow apenas de leituras e canário.
9. Cortar o Umbra por configuração de base URL, com rollback ensaiado.
10. Só então iniciar funcionalidades ainda não presentes no mock.

## 12. Critérios de aceite da transição

- OpenAPI sem breaking changes não autorizadas.
- Suíte black-box verde contra mock e sucessor.
- Corridas reais de booking e idempotência com um único vencedor.
- Cancelamento, remarcação e transições com rollback comprovado.
- DST gap/overlap equivalente ao baseline.
- Isolamento cross-tenant e Keycloak real validados.
- Redis indisponível não derruba API nem booking.
- Constraints conhecidas retornam 4xx determinístico.
- Load test mede insert normal/conflito, disponibilidade e tamanho do GiST.
- Cutover e rollback documentados e ensaiados.

## 13. Rastreabilidade

- Baseline Java: `../../gnomon-mock/docs/prd.md` e `../../gnomon-mock/docs/adr`.
- Transição do mock: `../../gnomon-mock/docs/adr/0025-conversao-gnomon-mock.md`.
- Consumidor: `../../umbra` e sua documentação de migração do Gnomon.
