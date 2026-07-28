# Fase 08 — Cancelamento e remarcação via token (público)

Status: todo

## Objetivo

Cliente final cancela ou remarca sem login, autorizado por token seguro por appointment.

## Escopo

- Geração de `cancel_token`/`reschedule_token` (aleatórios, longos, não previsíveis) na criação
  do appointment, persistidos apenas como hash (`cancel_token_hash`, `reschedule_token_hash` —
  **migration aditiva desta fase**, ADR 0017: zero colunas mortas); tokens entregues na resposta
  de booking e em futuras notificações.
- `POST /v1/public/appointments/{id}/cancel` com token: valida hash, appointment `scheduled`;
  transação: `SELECT ... FOR UPDATE`, marca `cancelled`, remove `appointment_slots` (horário
  volta a ficar disponível), commit.
- `POST /v1/public/appointments/{id}/reschedule` com token + `new_start_at`: valida token,
  disponibilidade do novo horário; transação: remove slots antigos, insere novos (mesma
  proteção de constraint), atualiza `start_at`/`end_at`; conflito → rollback mantendo o
  appointment anterior e 409.
- Tokens de uso único por ação; comparação de hash em tempo constante. Cancelamento consome os
  hashes após commit; remarcação bem-sucedida rotaciona ambos e conflito preserva os tokens
  anteriores. Appointments anteriores à migration, com hashes nulos, não expõem ações públicas.

## Fora de escopo

- Revogação administrativa/rotação avulsa de tokens; janela mínima de antecedência para
  cancelar (regra de política futura); notificação de cancelamento.

## Testes

- Integração: token inválido → 403; cancel libera slots; remarcação para horário ocupado
  mantém original (409); remarcação válida move slots atomicamente; concorrência de remarcação
  × booking no mesmo horário (constraint decide).

## Critérios de aceite

- [ ] Fluxos públicos de cancel/remarcação funcionando sem auth, apenas com token.
- [ ] Nenhum token em texto puro persistido ou logado.

## Notas de implementação

(preencher ao concluir)
