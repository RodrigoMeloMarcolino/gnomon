# ADR 0017 — Disciplina de schema: índices de FK, `updated_at` real, status tipados, zero colunas mortas

Status: Accepted
Data: 2026-07-23

## Contexto

O schema do Moira acumulou quatro dívidas estruturais que pareciam inofensivas isoladamente:

1. **FKs sem índice**: PostgreSQL não cria índice automaticamente para o lado filho de uma FK.
   `appointments.provider_id`, `offerings.provider_id` e `appointment_slots.appointment_id`
   ficaram sem índice, degradando todas as consultas tenant/provider-scoped.
2. **`updated_at` falso**: a coluna existia com `server_default=now()` mas nenhum mecanismo de
   atualização (`onupdate`, trigger ou hook) — auditoria created/updated era mentirosa. Auditoria
   falsa é pior que ausente.
3. **Status como string livre**: `appointments.status` era `String(32)` sem enum, CHECK ou
   máquina de estados — qualquer valor podia ser persistido.
4. **Colunas mortas**: `cancel_token_hash` e `reschedule_token_hash` foram criadas "para uso
   futuro" e nunca ganharam fluxo — interfaces de intenção falsa que confundem leitores e
   ferramentas.

O Gnomon define DDLs em specs antes das migrations; essas disciplinas custam quase zero quando
aplicadas no nascimento do schema e são caras como refatoração.

## Decisão

1. **Índice obrigatório em toda FK**: toda coluna de chave estrangeira recebe índice na mesma
   migration que a cria (composto com `tenant_id` quando a tabela for tenant-owned, alinhado aos
   padrões de consulta tenant-scoped).
2. **`updated_at` só existe se for mantido**: tabelas com `updated_at` recebem trigger de banco
   que a atualiza em todo `UPDATE`; tabelas imutáveis ou append-only (ex.: `appointment_slots`)
   não têm a coluna. Sem mecanismo de atualização, a coluna não é criada.
3. **Status tipados com máquina de estados**: colunas de status usam `CHECK` com o conjunto
   fechado de valores, e a spec do módulo documenta as transições válidas. Nenhum status é
   string livre.
4. **Zero colunas mortas**: nenhuma coluna é criada antes da fase que a utiliza. Colunas de
   funcionalidades futuras (ex.: `cancel_token_hash`/`reschedule_token_hash`, usadas só na fase
   08) nascem em migration aditiva da própria fase — migrations aditivas já são a regra (ADR
   0003, convenção Flyway).

## Consequências

- Consultas tenant-scoped nascem indexadas; EXPLAIN guiado vira otimização, não correção.
- Auditoria temporal confiável onde existir; ausência explícita onde não fizer sentido.
- Schema reflete apenas comportamento implementado — leitura do DDL é documentação verdadeira.
- Pequenas migrations aditivas a mais no histórico (ex.: fase 08), em troca de zero colunas sem
  fluxo.

## Rastreabilidade

- Origem: dívidas Moira — FKs sem índice, `updated_at` sem `onupdate`, `status` string livre,
  `cancel/reschedule_token_hash` sem fluxo. Documentado na task
  `docs/tasks/00.5-hardening-licoes-moira.md`.
- Complementa: ADR 0003 (migrations aditivas), ADR 0016 (constraints nomeadas com tradução).
