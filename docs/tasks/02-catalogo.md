# Fase 02 — Catálogo: colaboradores, calendários, offerings

Status: done (concluída em 2026-07-28)

## Objetivo

Owner/admin consegue montar a estrutura pública do tenant: equipe (colaboradores + calendários)
e catálogo de serviços com atribuição.

## Escopo

- Migrations: `collaborators`, `calendars`, `offerings`, `calendar_offerings` (spec
  multi-tenancy 3.4–3.5 + spec booking seção 3).
- Use cases (todos tenant-scoped com validação de role):
  - CRUD colaboradores (criar já gera o calendário 1:1 na mesma transação; remoção é
    desativação lógica e preserva histórico);
  - vincular/desvincular `user_id` ↔ collaborator (cria/ajusta membership `staff`);
  - editar calendário (nome, timezone, is_active) — owner/admin; staff apenas leitura do
    próprio;
  - CRUD offerings (duração múltipla de 15, preço em centavos, unicidade de título ativo;
    remoção é desativação lógica);
  - atribuição `PUT /v1/tenants/{slug}/calendars/{calendarId}/offerings` (substitui o conjunto
    atribuído).
- Endpoints públicos (sem auth, apenas leitura de ativos):
  - `GET /v1/public/tenants/{slug}` (perfil público do tenant);
  - `GET /v1/public/tenants/{slug}/calendars` (calendários ativos + nome do colaborador);
  - `GET /v1/public/tenants/{slug}/offerings` (serviços ativos; suporta `?calendar_id=` para
    filtrar por atribuição).
- Respostas públicas sem `user_id` nem dados de conta (ADR 0014).

## Fora de escopo

- Availability rules e cálculo de slots (fase 03); bio/foto de colaborador; slug público por
  calendário (decisão aberta do PRD — se adotado, registrar aqui).

## Testes

- Integração: criar colaborador cria calendário atomicamente; vínculo staff concede acesso só
  ao próprio calendário; CRUD administrativo cross-tenant 403 e público fora do escopo 404;
  catálogo público só mostra ativos;
  filtro por atribuição.
- Unit: validação de duração (%15), preço >= 0, unicidade de título.

## Critérios de aceite

- [x] Fluxo owner: criar colaborador → calendário existe → cadastrar offering → atribuir →
      aparece no público filtrado.
- [x] Matriz de autorização da spec multi-tenancy coberta para catálogo.

## Notas de implementação

- A migration aditiva `V3__catalog.sql` criou `collaborators`, `calendars`, `offerings` e
  `calendar_offerings`, com `tenant_id`, FKs compostas, uniques parciais, CHECKs, índices e
  triggers de `updated_at`.
- Domínio e aplicação permanecem separados de JPA. Os adapters tenant-scoped traduzem acesso
  administrativo cross-tenant para `403` e lookup público fora do tenant para `404`.
- Criar colaborador também cria seu calendário 1:1 na mesma transação. Desativar colaborador
  desativa o calendário e remove somente membership `staff`; memberships `owner/admin` são
  preservadas.
- O vínculo de staff é idempotente e resistente a corrida via
  `INSERT ... ON CONFLICT DO NOTHING`.
- Offerings usam preço em centavos, duração múltipla de 15 minutos, título ativo único e soft
  delete. A substituição de atribuições calendário–offering é transacional.
- `CatalogExceptionHandler` mantém o envelope
  `{"error":{"code","message","details"}}` para validação, conflito, ausência e autorização.
- Validação conjunta:
  - `spotless:check` + `test`: 92 testes, zero falhas;
  - ArchUnit: 4 testes incluídos na suíte, zero falhas;
  - `verify -Pintegration`: 19 testes, zero falhas, com PostgreSQL 16, Keycloak 26, Flyway
    V1–V3 e `ddl-auto=validate`;
  - jornada integrada owner → colaborador/calendário → offering → atribuição → catálogo público,
    incluindo soft delete, duplicidade e isolamento cross-tenant.

Risco/follow-up: o starter Redis já presente faz o Spring Data sondar repositories JPA durante o
startup e emitir mensagens informativas. A configuração explícita dos repositories Redis pertence
à fase 05 e não afeta o comportamento ou readiness atual.
