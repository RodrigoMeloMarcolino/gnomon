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
    `start_time < end_time`, `weekday` 1–7 **e alinhamento de 15 min em `start_time`/`end_time`
    (minuto ∈ {0,15,30,45}, segundos zerados) — validação simétrica na escrita + CHECKs na
    migration (ADR 0016, spec booking seção 3). Nenhuma regra que quebraria o cálculo pode ser
    persistida (regressão da dívida Moira do `09:07`)**;
  - `ListAvailableSlotsUseCase` (spec booking 5.1) com todas as validações de escopo/atribuição.
- Endpoint público:
  `GET /v1/public/tenants/{slug}/available-slots?calendar_id=&offering_id=&date=`
  retornando `{"available_start_times":["2027-07-01T12:00:00Z"]}`.

## Fora de escopo

- Booking (fase 04); cache deste endpoint (fase 05); exceções/bloqueios pontuais de agenda
  (futuro).

## Testes

- Unit do domínio (bateria da spec booking seção 7, incluindo DST: gap ignorado e overlap com
  dois instantes UTC; clock injetável).
- Integração: regras CRUD com autorização; **escrita com horário desalinhado (ex.: `09:07`) →
  422 na borda e CHECK no banco, sem derrubar o available-slots**; available-slots com offering
  não atribuído → 404; data local do calendário respeitada; resposta em UTC.

## Critérios de aceite

- [ ] Domínio de disponibilidade 100% testado sem banco.
- [ ] Endpoint público retorna apenas horários realmente livres e futuros.
- [ ] Validação simétrica: escrita inválida rejeitada; tabela de tradução de constraints de
  `availability_rules` (spec booking 6.1) implementada — nenhuma violação conhecida vira 500.

## Notas de implementação

(preencher ao concluir)
