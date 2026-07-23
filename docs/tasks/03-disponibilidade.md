# Fase 03 — Availability rules e available-slots

Status: todo

## Objetivo

Cálculo dinâmico de disponibilidade por calendário funcionando, com regras semanais e endpoint
público — sem persistir slots livres.

## Escopo

- Migration `availability_rules` (spec booking seção 3) + índice `(calendar_id, weekday)`.
- Domínio puro (sem Spring/JPA): geração de candidatos em step de 15 min, deduplicação de
  regras sobrepostas, corte por fim de janela, remoção de ocupados e de passado (clock
  injetável), conversão explícita via `ZoneId` do calendário (spec booking 4.2).
- Use cases:
  - CRUD `availability_rules` (owner/admin; staff no próprio calendário), validando
    `start_time < end_time` e `weekday` 1–7;
  - `ListAvailableSlotsUseCase` (spec booking 5.1) com todas as validações de escopo/atribuição.
- Endpoint público:
  `GET /v1/public/tenants/{slug}/available-slots?calendar_id=&offering_id=&date=`
  retornando instantes UTC (`["2027-07-01T12:00:00Z"]`).

## Fora de escopo

- Booking (fase 04); cache deste endpoint (fase 05); exceções/bloqueios pontuais de agenda
  (futuro).

## Testes

- Unit do domínio (bateria da spec booking seção 7, incluindo DST e clock injetável).
- Integração: regras CRUD com autorização; available-slots com offering não atribuído → 404;
  data local do calendário respeitada; resposta em UTC.

## Critérios de aceite

- [ ] Domínio de disponibilidade 100% testado sem banco.
- [ ] Endpoint público retorna apenas horários realmente livres e futuros.

## Notas de implementação

(preencher ao concluir)
