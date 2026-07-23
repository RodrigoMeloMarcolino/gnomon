# ADR 0012 — Snapshot de duração no appointment; durações múltiplas de 15

Status: Accepted
Data: 2026-07-23

## Contexto

Se o tenant editar a duração de um serviço, appointments já criados não podem mudar. E a
slotização de 15 minutos (ADR 0010) precisa de durações compatíveis para ser determinística.

## Decisão

1. Todo appointment grava **`duration_minutes_snapshot`** no momento da criação. `end_at` e os
   slots derivam do snapshot, nunca do valor vigente do offering.
2. Durações de offerings devem ser **múltiplos positivos de 15 minutos** no MVP
   (`duration_minutes > 0 AND duration_minutes % 15 = 0`), rejeitados na criação/atualização
   (validação de domínio + check constraint no banco).
3. O appointment também grava `calendar_timezone_snapshot` para consistência histórica.

## Consequências

- Edição/remoção de atribuição de serviço nunca reescreve histórico.
- O catálogo pode evoluir livremente sem efeitos colaterais em agenda já marcada.
- Se durações fora do múltiplo de 15 forem aceitas no futuro, a estratégia de slotização deve
  ser revista antes (novo ADR).

## Rastreabilidade

- Herda integralmente: Moira ADRs 0008 e 0009; livedoc 6.3/6.6/15.4.
