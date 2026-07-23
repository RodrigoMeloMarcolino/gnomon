# ADR 0006 — Calendário como dono da agenda

Status: Accepted
Data: 2026-07-23

## Contexto

No Moira, o `provider` era o dono da agenda: availability rules, appointments e a constraint
anti-double-booking (`UNIQUE(provider_id, slot_start_at)`) viviam no nível do provider. No
Gnomon, cada tenant pode ter múltiplos calendários (um por colaborador) e o cliente final
agenda com um calendário específico do tenant. É preciso eleger o novo dono da agenda.

## Decisão

1. **`calendars` é o dono da agenda**: `availability_rules`, `appointments` e
   `appointment_slots` referenciam `calendar_id`. O lock de concorrência sobe para
   `UNIQUE(calendar_id, slot_start_at)` (ADR 0011).
2. **1:1 collaborator ↔ calendar no MVP**: todo colaborador criado recebe automaticamente um
   calendário (`UNIQUE(tenant_id, collaborator_id)` em `calendars`). O calendário carrega
   `name` público (default: nome do colaborador) e `is_active`.
3. **Timezone no calendário**: `calendars.timezone` governa as regras semanais e a data local
   de disponibilidade, com default herdado de `tenants.timezone` no cadastro. Colaborador em
   outra região pode ter timezone própria.
4. O tenant continua dono do **catálogo** (offerings — ADR 0007) e do isolamento
   administrativo (`tenant_id` presente também nas tabelas de agenda, para scoping direto).
5. Campos públicos do calendário expostos na API pública: `id`, `name`, nome do colaborador e
   timezone. Nunca `user_id` nem dados de conta.

## Consequências

- Disponibilidade, booking, cancelamento e remarcação são sempre por calendário; "agenda do
  tenant" é a união das agendas dos seus calendários.
- Dois colaboradores do mesmo tenant podem atender no mesmo horário sem conflito (constraints
  independentes por calendário).
- Desativar um calendário (`is_active=false`) o remove do público sem apagar histórico.
- Múltiplos calendários por colaborador no futuro exige apenas remover a constraint 1:1 e
  adicionar seleção de calendário no cadastro — sem refatoração estrutural.
- O `appointments` mantém `calendar_timezone_snapshot` para consistência histórica (herdado do
  livedoc 15.8, que previa `provider_timezone_snapshot`).

## Rastreabilidade

- Decisão nova do Gnomon; generaliza o papel do `provider` do Moira (livedoc 6.2) para o
  modelo multi-calendário do PRD seção 5.
- Relacionados: ADRs 0003, 0005, 0007, 0011.
