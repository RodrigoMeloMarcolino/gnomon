# Fase 02 — Catálogo: colaboradores, calendários, offerings

Status: todo

## Objetivo

Owner/admin consegue montar a estrutura pública do tenant: equipe (colaboradores + calendários)
e catálogo de serviços com atribuição.

## Escopo

- Migrations: `collaborators`, `calendars`, `offerings`, `calendar_offerings` (spec
  multi-tenancy 3.4–3.5 + spec booking seção 3).
- Use cases (todos tenant-scoped com validação de role):
  - CRUD colaboradores (criar já gera o calendário 1:1 na mesma transação);
  - vincular/desvincular `user_id` ↔ collaborator (cria/ajusta membership `staff`);
  - editar calendário (nome, timezone, is_active) — owner/admin; staff apenas leitura do
    próprio;
  - CRUD offerings (duração múltipla de 15, preço em centavos, unicidade de título ativo);
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
  ao próprio calendário; CRUD com cross-tenant 403/404; catálogo público só mostra ativos;
  filtro por atribuição.
- Unit: validação de duração (%15), preço >= 0, unicidade de título.

## Critérios de aceite

- [ ] Fluxo owner: criar colaborador → calendário existe → cadastrar offering → atribuir →
      aparece no público filtrado.
- [ ] Matriz de autorização da spec multi-tenancy coberta para catálogo.

## Notas de implementação

(preencher ao concluir)
