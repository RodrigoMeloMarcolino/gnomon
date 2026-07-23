# Fase 07 — Painel administrativo (appointments e customers)

Status: todo

## Objetivo

Owner/admin acompanham a agenda do tenant; staff acompanha a própria agenda.

## Escopo

- `GET /v1/tenants/{tenantSlug}/appointments` com filtros `date` (ou range), `calendar_id`,
  `status` e paginação; staff recebe apenas o próprio calendário (filtro forçado).
- `GET /v1/tenants/{tenantSlug}/appointments/{id}` (detalhe com customer e serviço).
- Transições administrativas: `POST .../appointments/{id}/cancel`, `/complete`, `/no-show`
  (apenas de `scheduled`; cancel libera os slots na mesma transação — antecipa a regra de
  liberação de slots da fase 08 para o caminho admin).
- `GET /v1/tenants/{tenantSlug}/customers` e `/customers/{id}`: customers com appointments no
  tenant (inferência via appointments), campos globais mínimos.
- Eventos admin P1 de logging (`appointment.cancelled/completed/no_show`).

## Fora de escopo

- Edição de appointment; remarcação admin; exportações; métricas de agenda.

## Testes

- Integração: filtros combinados; staff restringido ao próprio calendário (403/missing fora
  dele); cancel admin libera slots (available-slots volta a mostrar o horário); transições
  inválidas (de `cancelled`) → 409; cross-tenant 403/404.

## Critérios de aceite

- [ ] Matriz de autorização da spec multi-tenancy coberta para appointments/customers.
- [ ] Cancelamento admin libera horário comprovadamente.

## Notas de implementação

(preencher ao concluir)
