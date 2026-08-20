# ADR 0005 — Booking com exclusion constraint GiST

Status: Accepted
Data: 2026-08-19

## Decisão

O novo schema não possui `appointment_slots`. `appointments` é histórico e garantia concorrente
por exclusion constraint GiST sobre `(tenant_id, calendar_id, tstzrange(start_at,end_at,'[)'))`,
parcial para `status = 'scheduled'`.

A unique de idempotência permanece separada. Disponibilidade lê intervals de appointments e
continua oferecendo candidatos de 15 minutos. Cancelamento remove logicamente o intervalo do
índice ao mudar status; remarcação disputa a constraint no mesmo `UPDATE` transacional.

`completed` e `no_show` só podem ser aplicados após `end_at`; scheduled vencidos possuem política
operacional explícita. Toda violação da constraint nomeada retorna `409 slot_unavailable`.

## Consequências

- Uma linha por appointment e ausência de scheduler/retenção de slots.
- Overlaps arbitrários são impedidos, com intervalos consecutivos permitidos.
- GiST precisa de benchmark, métricas, backup/restore e teste de carga antes do cutover.
- Particionamento futuro de appointments precisa considerar limitações de exclusion constraints.
