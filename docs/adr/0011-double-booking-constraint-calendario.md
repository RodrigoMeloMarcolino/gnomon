# ADR 0011 — Prevenção de double booking via `UNIQUE(calendar_id, slot_start_at)`

Status: Superseded by ADR-0022
Data: 2026-07-23

## Contexto

Dois clientes podem tentar o mesmo horário no mesmo calendário quase simultaneamente; ambos
podem ler a agenda como livre antes de qualquer escrita. Validação de leitura não é garantia.
No Gnomon, o recurso disputado é o calendário (ADR 0006), não mais o provider.

## Decisão

1. A prevenção final de double booking é feita pelo banco:
   **PRIMARY KEY(tenant_id, calendar_id, slot_start_at) em `appointment_slots`**.
2. Criação de appointment + inserção de todos os slots ocupados na **mesma transação curta**,
   junto com busca/criação do customer. Violação da constraint → rollback total.
3. A violação é traduzida para erro de domínio "horário indisponível" → **HTTP 409 Conflict**
   (envelope padrão, código estável).
4. Não chamar serviços externos dentro da transação.
5. Concorrência é testada com testes de integração contra PostgreSQL real (Testcontainers):
   duas tentativas simultâneas para o mesmo slot; uma vence, a outra recebe 409.

## Consequências

- Colaboradores diferentes do mesmo tenant podem ocupar o mesmo horário (constraints
  independentes por calendário) — comportamento desejado.
- `appointment_slots` duplica `calendar_id` propositalmente (além de `appointment_id`) para
  sustentar a constraint sem join. A retenção dos locks é definida no ADR 0022.
- O primeiro commit vence; o segundo cliente escolhe outro horário. Sem reserva temporária no
  MVP.
- Cancelamento futuro remove os slots na mesma transação em que marca `cancelled`, liberando o
  horário (recomendação do livedoc 33.2).

## Rastreabilidade

- Herda: Moira ADR 0007; livedoc seção 11 e 15.6.1 (fluxo síncrono transacional).
- Mudança de escopo: `provider_id` → `calendar_id`.
