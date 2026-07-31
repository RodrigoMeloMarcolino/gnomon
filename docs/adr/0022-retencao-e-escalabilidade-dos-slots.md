# ADR 0022 — Retenção e escalabilidade dos locks de slots

Status: Accepted
Data: 2026-07-30

## Contexto

`appointment_slots` existe para garantir concorrência, mas o histórico definitivo já está em
`appointments`. Reter cada lock indefinidamente multiplica o volume por duração do serviço e
torna a manutenção do índice global desnecessariamente cara.

## Decisão

1. `appointment_slots` é um lock transacional temporário; `appointments` é a fonte histórica.
2. O booking público aceita apenas inícios até 180 dias no futuro (`gnomon.booking.max-advance-days`).
3. Slots com mais de 30 dias são removidos por um job em lotes curtos, com `FOR UPDATE SKIP LOCKED`.
4. A chave natural `(tenant_id, calendar_id, slot_start_at)` passa a ser a PK e a garantia de
   double booking. O UUID artificial do slot é removido.
5. O índice `(slot_start_at)` sustenta a retenção; o índice `(tenant_id, appointment_id)` é
   mantido para FK, cascade e operações tenant-scoped.
6. Particionamento mensal por `slot_start_at` será considerado quando a relação ultrapassar
   50 milhões de linhas ou 15 GiB, ou quando a consulta p95 exceder 20 ms.
7. A operação deve expor métricas sem `tenant` como label: linhas vivas e tamanho da relação,
   idade do lock mais antigo, atraso/duração da limpeza, latência de leitura de ocupados,
   duração dos inserts e conflitos de booking.

## Consequências

- Cancelamento libera slots imediatamente; appointments concluídos, `no_show` e cancelados
  permanecem históricos mesmo depois da remoção dos locks.
- O contrato HTTP de booking não muda, exceto pela rejeição determinística de datas além do
  horizonte (`422 validation_error`).
- A PK tenant/calendário/instante continua equivalente à unique anterior porque calendários têm
  identidade global e a FK composta impede que um calendário pertença a outro tenant.
- Uma futura escala acima de 100 milhões de slots deve comparar particionamento com uma
  exclusion constraint GiST diretamente em `appointments`. O runbook, os gates, o rollout e o
  rollback estão documentados em
  [`docs/specs/appointment-gist-migration.md`](../specs/appointment-gist-migration.md).
