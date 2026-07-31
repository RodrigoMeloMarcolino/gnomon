# Plano de migração futura — exclusão GiST em `appointments`

Status: **condicional; pronto para avaliação, não autorizado para execução**

Decisão relacionada: ADR 0022

Última atualização: 2026-07-31

## 1. Objetivo

Substituir, quando os gatilhos de escala justificarem, a garantia de concorrência baseada em
várias linhas de `appointment_slots` por uma exclusion constraint GiST diretamente em
`appointments`. O estado final deve:

- manter uma linha histórica por appointment;
- impedir sobreposição temporal no mesmo tenant/calendário sob concorrência real;
- permitir appointments consecutivos, porque os intervalos usam a convenção `[start_at, end_at)`;
- preservar `409 slot_unavailable`, idempotência, rollback e isolamento multi-tenant;
- calcular disponibilidade sem depender de `appointment_slots`;
- liberar o intervalo ao cancelar um appointment;
- permitir remover `appointment_slots`, seu job de retenção e seus índices somente após um
  período seguro de convivência.

Este documento é um runbook futuro. Não reserva número de migration, não autoriza DDL em
produção e não altera a decisão vigente do ADR 0022.

## 2. Por que a evolução é possível

`appointments` já possui todos os campos necessários: `tenant_id`, `calendar_id`, `start_at`,
`end_at` e `status`. A fonte histórica já é `appointments`; `appointment_slots` funciona apenas
como lock discreto de concorrência.

O modelo futuro usa:

```sql
tstzrange(start_at, end_at, '[)')
```

e rejeita duas linhas quando, simultaneamente:

1. o `tenant_id` é igual;
2. o `calendar_id` é igual;
3. os intervalos se sobrepõem pelo operador `&&`;
4. ambas participam do predicado da constraint.

`btree_gist` fornece as operator classes GiST de igualdade para UUID, enquanto o PostgreSQL
oferece o operador de sobreposição para `tstzrange`.

## 3. Gatilho de decisão

A migração não deve começar apenas porque GiST existe. Abrir a avaliação quando ocorrer pelo
menos uma condição:

- projeção acima de 100 milhões de slots na janela ativa;
- custo de `appointment_slots` permanecer relevante mesmo após horizonte e retenção do ADR 0022;
- inserts de slots ou manutenção da tabela ultrapassarem o SLO acordado;
- particionamento de `appointment_slots` não entregar custo/complexidade aceitável;
- benchmark representativo demonstrar vantagem clara do modelo GiST.

Antes de aprovar a execução, comparar pelo menos:

| Dimensão | Slots discretos | GiST em appointments |
| --- | --- | --- |
| Linhas de lock | duração ÷ 15 minutos | uma linha por appointment |
| Garantia final | PK dos slots | exclusion constraint |
| Leitura de ocupação | slots prontos | intervalos, expandidos para o port atual |
| Cancelamento | remove slots | muda `status` e sai do índice parcial |
| DDL de adoção | já vigente | construção bloqueante da constraint |
| Particionamento | mensal por `slot_start_at` | restrição importante no PostgreSQL 16 |

O resultado do benchmark e a decisão final devem atualizar o ADR 0022. Se a solução escolhida
não for GiST diretamente em `appointments`, criar um novo ADR.

## 4. Semântica alvo

### 4.1 Intervalo

Usar intervalo semiaberto `[)`:

- appointment A `[09:00, 10:00)` e B `[10:00, 11:00)` podem coexistir;
- appointment A `[09:00, 10:00)` e B `[09:45, 10:15)` conflitam;
- timezone não participa da constraint: `TIMESTAMPTZ` representa instantes; a timezone do
  calendário continua sendo usada para regras semanais e datas locais.

### 4.2 Status que bloqueia

O desenho recomendado é indexar somente:

```sql
WHERE (status = 'scheduled')
```

Isso mantém o índice proporcional aos appointments ainda ativos. Antes da migração, as fases 07
e 08 precisam garantir:

- `completed` e `no_show` só podem ser aplicados quando o appointment já terminou;
- `cancelled` libera o horário imediatamente;
- remarcação mantém `status = 'scheduled'` e altera o intervalo atomicamente;
- appointments `scheduled` antigos têm política operacional definida, para não crescerem
  indefinidamente no índice parcial.

Se o produto permitir `completed` ou `no_show` antes de `end_at`, o predicado precisa incluir
esses estados. Isso aumenta o índice indefinidamente e deve ser aprovado explicitamente no ADR.
Não usar `now()` no predicado: predicados de índice precisam ser imutáveis.

### 4.3 Isolamento

Mesmo que `calendars.id` seja globalmente único, manter `tenant_id` na constraint:

- torna o isolamento visível no DDL;
- segue o padrão tenant-scoped do projeto;
- protege futuras mudanças de identidade;
- permite consultas alinhadas a `(tenant_id, calendar_id, período)`.

## 5. Schema alvo

O SQL abaixo é a forma alvo, não um arquivo Flyway pronto:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_tenant_calendar_period
    EXCLUDE USING gist (
        tenant_id WITH =,
        calendar_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status = 'scheduled');
```

Regras:

- constraint sempre nomeada como `ex_appointments_tenant_calendar_period`;
- não declarar `DEFERRABLE`: o conflito deve aparecer no statement que insere ou remarca;
- não usar a exclusion constraint como arbiter de `ON CONFLICT`; a idempotência continua usando
  `uq_appointments_tenant_idempotency_key`;
- manter os índices B-tree exigidos por listagem administrativa e ordenação. O GiST não
  substitui automaticamente `(tenant_id, calendar_id, start_at)` nem
  `(tenant_id, status, start_at)`;
- revisar índices redundantes somente depois de `EXPLAIN (ANALYZE, BUFFERS)` em produção
  representativa.

### 5.1 Limitação operacional do PostgreSQL 16

`ALTER TABLE ... ADD CONSTRAINT EXCLUDE`:

- verifica os dados existentes;
- cria o índice que sustenta a constraint;
- não aceita `NOT VALID`;
- não oferece caminho para anexar depois um GiST criado com `CREATE INDEX CONCURRENTLY`;
- exige lock de DDL e deve ser tratado como mudança com janela de manutenção.

Não colocar essa criação em uma migration comum disparada automaticamente no startup de todas
as réplicas. A implantação deve ter um passo de migration controlado, `lock_timeout`,
`statement_timeout`, monitoramento de progresso e rollback por abortar a migration.

Durante essa etapa, desabilitar Flyway nos processos normais da aplicação e usar um único
migration runner. Os valores de timeout devem vir do ensaio de staging; a migration pode usar
`SET LOCAL` antes do `ALTER TABLE`, por exemplo:

```sql
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

ALTER TABLE appointments
    ADD CONSTRAINT ex_appointments_tenant_calendar_period
    EXCLUDE USING gist (
        tenant_id WITH =,
        calendar_id WITH =,
        tstzrange(start_at, end_at, '[)') WITH &&
    )
    WHERE (status = 'scheduled');
```

Os tempos acima são ilustrativos, não defaults aprovados.

### 5.2 Particionamento

No PostgreSQL 16, uma tabela particionada não aceita exclusion constraint global no pai.
Constraints em partições isoladas não impedem conflito entre partições.

Consequências:

- não particionar `appointments` por mês antes desta decisão;
- se `appointments` já estiver particionada, interromper este runbook;
- uma alternativa por hash de calendário, garantindo que todo calendário caia em uma única
  partição, exige novo desenho, benchmark e ADR;
- particionamento mensal de `appointment_slots` e GiST direto em `appointments` são caminhos
  arquiteturais diferentes, não etapas automaticamente cumulativas.

## 6. Pré-condições obrigatórias

Antes da primeira release de migração:

- [ ] ADR 0022 atualizado com benchmark e decisão de executar;
- [ ] número Flyway atual consultado; nenhum `V{NEXT}` reservado antecipadamente;
- [ ] PostgreSQL alvo possui `btree_gist` disponível;
- [ ] backup e restore testados em ambiente equivalente;
- [ ] janela de DDL aprovada, com orçamento de lock e duração;
- [ ] nenhuma sobreposição entre appointments que participarão da constraint;
- [ ] nenhuma linha com `start_at >= end_at`;
- [ ] semântica de `completed`, `no_show`, cancelamento e remarcação fechada;
- [ ] handler compatível com a constraint nova implantado antes do DDL;
- [ ] testes de concorrência executados em PostgreSQL real;
- [ ] dashboards e alertas de booking disponíveis sem label de tenant;
- [ ] plano de rollback ensaiado.

## 7. Auditorias pré-migração

### 7.1 Extensão

```sql
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name = 'btree_gist';
```

### 7.2 Intervalos inválidos

```sql
SELECT count(*) AS invalid_intervals
FROM appointments
WHERE start_at >= end_at;
```

Resultado obrigatório: zero.

### 7.3 Sobreposições entre scheduled

```sql
WITH ordered AS (
    SELECT id,
           tenant_id,
           calendar_id,
           start_at,
           end_at,
           max(end_at) OVER (
               PARTITION BY tenant_id, calendar_id
               ORDER BY start_at, end_at, id
               ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
           ) AS previous_max_end
    FROM appointments
    WHERE status = 'scheduled'
)
SELECT *
FROM ordered
WHERE start_at < previous_max_end
ORDER BY tenant_id, calendar_id, start_at;
```

Resultado obrigatório: zero. Qualquer linha deve ser investigada; não corrigir automaticamente
histórico por uma migration.

### 7.4 Scheduled antigos

```sql
SELECT count(*) AS stale_scheduled,
       min(end_at) AS oldest_scheduled_end
FROM appointments
WHERE status = 'scheduled'
  AND end_at < CURRENT_TIMESTAMP;
```

Definir se esses registros são válidos, precisam de transição administrativa ou revelam falha
de processo. A correção é uma operação de domínio auditável, não um `UPDATE` cego de migration.

### 7.5 Paridade entre appointments e slots

Durante a convivência, comparar slots esperados dos appointments scheduled com os locks
persistidos, limitando a consulta a uma janela operacional:

```sql
WITH expected AS (
    SELECT a.tenant_id,
           a.calendar_id,
           a.id AS appointment_id,
           generate_series(
               a.start_at,
               a.end_at - interval '15 minutes',
               interval '15 minutes'
           ) AS slot_start_at
    FROM appointments a
    WHERE a.status = 'scheduled'
      AND a.end_at >= CURRENT_TIMESTAMP - interval '30 days'
      AND a.start_at <= CURRENT_TIMESTAMP + interval '180 days'
),
missing AS (
    SELECT * FROM expected
    EXCEPT
    SELECT tenant_id, calendar_id, appointment_id, slot_start_at
    FROM appointment_slots
),
unexpected AS (
    SELECT tenant_id, calendar_id, appointment_id, slot_start_at
    FROM appointment_slots
    EXCEPT
    SELECT * FROM expected
)
SELECT (SELECT count(*) FROM missing) AS missing_slots,
       (SELECT count(*) FROM unexpected) AS unexpected_slots;
```

Diferenças só são aceitáveis quando explicadas pela retenção ou pelo estado do appointment.

## 8. Sequência de releases e migrations

### Etapa 0 — benchmark e decisão

1. Reproduzir carga e distribuição reais.
2. Medir tamanho, WAL, latência de insert, conflito e leitura.
3. Comparar slots particionados, GiST direto e, se necessário, uma tabela de ranges.
4. Aprovar a estratégia no ADR 0022.

Saída: decisão explícita; nenhuma alteração produtiva.

### Etapa 1 — release de compatibilidade

Implantar código que ainda usa slots, mas já reconhece
`ex_appointments_tenant_calendar_period`.

Alterações esperadas:

- `AppointmentJdbcAdapter.insert`: traduz violação da exclusion constraint para
  `BookingException("slot_unavailable", ...)`;
- `BookingExceptionHandler`: traduz a constraint para `409 slot_unavailable`;
- testes unitários dos dois caminhos de constraint;
- métrica separada para conflitos GiST, sem tenant como label.

Essa release precisa estar em toda a frota antes do DDL, evitando que um conflito novo vire 500.

### Etapa 2 — habilitar extensão

Criar migration aditiva com o próximo número livre:

```text
V{NEXT}__enable_btree_gist.sql
```

Conteúdo:

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
```

Validar a extensão em PostgreSQL local, Testcontainers e todos os provedores de produção.

### Etapa 3 — adicionar a exclusion constraint

Criar, somente após as auditorias:

```text
V{NEXT}__add_appointment_exclusion_constraint.sql
```

Requisitos operacionais:

1. retirar temporariamente os writers de booking ou colocar a API em modo que rejeite novas
   mutações;
2. aguardar transações de booking abertas terminarem;
3. aplicar `lock_timeout` curto para falhar sem esperar indefinidamente;
4. aplicar `statement_timeout` compatível com a janela;
5. executar a migration por um único job controlado;
6. confirmar constraint e índice em `pg_constraint`/`pg_indexes`;
7. executar imediatamente os testes de conflito e smoke;
8. reabrir os writers.

Como a migration é transacional, timeout, conflito preexistente ou cancelamento devem abortar o
DDL sem remover a proteção de slots.

### Etapa 4 — convivência com dupla proteção

Manter:

- exclusion constraint ativa em `appointments`;
- escrita e leitura de `appointment_slots`;
- limpeza de slots vigente;
- comparação de paridade e métricas por pelo menos um ciclo operacional acordado.

Não é necessário “dual write” adicional: o fluxo atual já insere primeiro o appointment e
depois seus slots na mesma transação. A nova constraint passa a proteger o primeiro insert, e a
PK dos slots continua protegendo o segundo.

### Etapa 5 — trocar a fonte de disponibilidade

Preservar o port `OccupiedSlotPort`; trocar apenas o adapter PostgreSQL para expandir os
intervalos em slots de 15 minutos na própria consulta:

```sql
SELECT generate_series(
           start_at,
           end_at - interval '15 minutes',
           interval '15 minutes'
       ) AS slot_start_at
FROM appointments
WHERE tenant_id = ?
  AND calendar_id = ?
  AND status = 'scheduled'
  AND tstzrange(start_at, end_at, '[)')
      && tstzrange(?::timestamptz, ?::timestamptz, '[)')
ORDER BY slot_start_at;
```

O adapter continua retornando `Set<Instant>`. Assim, o domínio de disponibilidade, o contrato do
port e os limites entre os módulos `availability` e `booking` permanecem estáveis.

Implantar primeiro em shadow mode:

- consulta slots e appointments;
- responde usando slots;
- registra somente contagem/hash de divergência, nunca IDs ou PII em labels/logs.

Depois de paridade sustentada, mudar a resposta para appointments.

### Etapa 6 — parar de escrever slots

Adicionar configuração temporária:

```yaml
gnomon:
  booking:
    slot-lock-writes-enabled: false
```

O default permanece `true` até a mudança ser aprovada. Quando desabilitado:

- `CreateAppointmentService` não gera locks persistidos;
- a exclusion constraint é a única garantia final;
- disponibilidade já lê `appointments`;
- cancelamento/remarcação deixa de depender de delete/insert de slots;
- idempotência e customer continuam na mesma transação curta.

Manter tabela, job e código de rollback por uma janela definida. Para reabilitar writes de slots,
é necessário preencher os locks ausentes antes de voltar a usá-los como garantia.

### Etapa 7 — remover infraestrutura de slots

Somente depois da janela de estabilidade:

1. remover `SlotRetentionScheduler`, `SlotRetentionBatchService`,
   `SlotRetentionJdbcAdapter` e suas configurações;
2. remover `insertSlots` do port/repository e o `SlotGenerator` do fluxo de persistência
   (ele pode continuar no domínio de disponibilidade);
3. remover traduções exclusivas das FKs/PK de `appointment_slots`;
4. criar migration destrutiva separada:

   ```text
   V{NEXT}__drop_appointment_slots.sql
   ```

5. executar `DROP TABLE appointment_slots` apenas após backup, restore ensaiado e aprovação;
6. atualizar PRD, spec de booking, ADR 0022, tasks e diagramas.

Nunca combinar criação da constraint e `DROP TABLE` na mesma migration.

## 9. Alterações de aplicação previstas

| Área | Mudança |
| --- | --- |
| `AppointmentRepository` | deixar de persistir slots após o cutover |
| `AppointmentJdbcAdapter` | mapear `ex_appointments_tenant_calendar_period` para conflito |
| `PostgresOccupiedSlotAdapter` | ler intervalos de `appointments` sem mudar o port inicialmente |
| `CreateAppointmentService` | remover geração/persistência de locks na etapa final |
| Cancelamento | `status = 'cancelled'` remove a linha do índice parcial na mesma transação |
| Remarcação | `UPDATE start_at/end_at` disputa a constraint; conflito faz rollback |
| Complete/no-show | validar término antes da transição para não liberar intervalo futuro |
| Retenção | remover job e configuração somente após abandono da tabela |
| Cache | manter invalidação pós-commit por dia/calendário |
| Observabilidade | medir inserts, conflitos, consultas, tamanho e divergência de shadow read |

Controllers, payloads e respostas HTTP não mudam.

## 10. Concorrência, idempotência e transações

- A leitura de disponibilidade continua advisory.
- O appointment deve ser inserido/atualizado dentro da mesma transação curta do customer e da
  idempotência.
- O `ON CONFLICT ON CONSTRAINT uq_appointments_tenant_idempotency_key DO NOTHING` continua
  dedicado à idempotência; conflito GiST não deve ser silenciado.
- Duas chaves diferentes disputando intervalos sobrepostos: uma confirma, outra recebe 409.
- Mesma chave e mesmo payload: um insert confirma e o outro faz replay do vencedor.
- Mesma chave e payload diferente: 409 `idempotency_key_conflict`, não `slot_unavailable`.
- Remarcação conflitante deve restaurar intervalo, status e tokens anteriores pelo rollback.
- Não introduzir advisory lock como garantia final.

## 11. Tradução determinística de constraints

Adicionar à tabela viva da spec de booking:

| Constraint | Código | HTTP |
| --- | --- | --- |
| `ex_appointments_tenant_calendar_period` | `slot_unavailable` | 409 |

Durante a convivência, tanto essa constraint quanto `pk_appointment_slots` precisam produzir o
mesmo contrato. Depois do `DROP TABLE`, remover apenas a entrada obsoleta.

## 12. Testes obrigatórios

### 12.1 Schema/Flyway

- extensão `btree_gist` instalada;
- constraint com nome, predicado e expressão corretos;
- migration falha diante de overlap preexistente;
- migrations partindo de banco vazio e de snapshot V6+;
- schema validado com `ddl-auto=validate`;
- nenhum número Flyway duplicado.

### 12.2 Integração PostgreSQL real

- mesmo calendário e mesmo intervalo: um sucesso e um 409;
- overlap parcial no início e no fim: 409;
- intervalo completamente contido: 409;
- intervalos consecutivos `[)` são aceitos;
- mesmo instante em calendários diferentes: aceito;
- tenants diferentes: independentes;
- cancelamento libera imediatamente;
- `completed`/`no_show` obedecem a regra temporal definida;
- remarcação livre confirma;
- remarcação conflitante faz rollback integral;
- corrida de idempotência mantém um appointment;
- conflito não deixa customer ou tokens órfãos;
- disponibilidade via appointments mantém paridade com slots;
- DST gap/overlap continua correto no cálculo de candidatos;
- múltiplas instâncias da API observam a mesma garantia.

### 12.3 Carga e operação

- benchmark com distribuição real de durações;
- p50/p95/p99 de insert normal e conflito;
- p95 da consulta diária de ocupação;
- tamanho de heap e índice GiST;
- WAL gerado na construção e na carga normal;
- duração e lock observado durante criação da constraint;
- `VACUUM`, backup e restore com o novo índice;
- teste de falha/timeout do DDL;
- teste de rollback antes e depois de desabilitar slots.

## 13. Métricas e alertas

Sem `tenant_id`, `calendar_id` ou `appointment_id` como labels:

- duração de insert/update de appointment;
- conflitos por constraint (`slot_pk` versus `appointment_gist` durante convivência);
- duração da consulta de ocupação;
- divergências do shadow read;
- linhas scheduled e scheduled vencidos;
- tamanho total de `appointments` e do índice GiST;
- duração da migration e tempo de espera por lock;
- taxa de 409 do booking.

Alertar pelo menos para:

- qualquer divergência de shadow read;
- conflito desconhecido traduzido como 500;
- scheduled vencidos acima do limite operacional;
- consulta p95 acima de 20 ms;
- crescimento anormal do índice;
- DDL aguardando lock além do orçamento.

## 14. Rollback

### Antes de desabilitar writes de slots

Rollback simples:

1. manter ou reverter a aplicação para leitura de slots;
2. a exclusion constraint pode permanecer, pois adiciona uma garantia equivalente/mais forte;
3. se necessário, removê-la em uma migration posterior, nunca alterando migration aplicada.

### Depois de desabilitar writes, antes de remover a tabela

Para voltar ao modelo de slots:

1. pausar writers;
2. reabilitar a geração de slots;
3. backfill de appointments que ainda bloqueiam, em lotes;
4. validar paridade zero;
5. voltar a ler slots;
6. reabrir writers.

Exemplo conceitual de backfill:

```sql
INSERT INTO appointment_slots (
    tenant_id,
    appointment_id,
    calendar_id,
    slot_start_at
)
SELECT a.tenant_id,
       a.id,
       a.calendar_id,
       generate_series(
           a.start_at,
           a.end_at - interval '15 minutes',
           interval '15 minutes'
       )
FROM appointments a
WHERE a.status = 'scheduled'
ON CONFLICT DO NOTHING;
```

Executar em lotes; não usar esse SQL irrestrito em produção.

### Depois de remover a tabela

Rollback exige uma nova migration que recrie tabela, constraints e índices, seguida do backfill.
Por isso, `DROP TABLE` só acontece após uma janela longa e aprovação explícita.

## 15. Alternativa quando zero downtime for obrigatório

Se a janela para criar a exclusion constraint diretamente em `appointments` for inviável,
interromper este runbook. Avaliar uma tabela compacta de locks por intervalo, por exemplo
`appointment_reservations`, com uma linha por appointment e GiST:

- criar tabela e constraint sem bloquear `appointments`;
- backfill concorrente;
- dual write;
- validar paridade;
- trocar a garantia.

Essa alternativa preserva a redução de 4×/8×, mas não é “GiST diretamente em appointments” e
precisa de novo ADR, nova spec e novo plano de retenção.

## 16. Critérios de aceite do cutover

- [ ] decisão e benchmark registrados no ADR;
- [ ] nenhuma sobreposição ou intervalo inválido antes do DDL;
- [ ] release de compatibilidade implantada antes da constraint;
- [ ] constraint GiST criada dentro do orçamento operacional;
- [ ] todas as violações conhecidas retornam 4xx determinístico;
- [ ] concorrência real cobre create, cancel e reschedule;
- [ ] shadow read sem divergência durante a janela acordada;
- [ ] SLOs de leitura e escrita atendidos;
- [ ] rollback ensaiado;
- [ ] slots deixam de ser escritos somente após aprovação;
- [ ] tabela removida em migration separada e tardia;
- [ ] PRD, ADR, spec, tasks e observabilidade sincronizados.

## 17. Referências

- PostgreSQL 16 — [Range Types e exclusion constraints](https://www.postgresql.org/docs/16/rangetypes.html)
- PostgreSQL 16 — [`btree_gist`](https://www.postgresql.org/docs/16/btree-gist.html)
- PostgreSQL 16 — [`CREATE TABLE` / `EXCLUDE`](https://www.postgresql.org/docs/16/sql-createtable.html)
- PostgreSQL 16 — [Restrições de tabela](https://www.postgresql.org/docs/16/ddl-constraints.html)
- PostgreSQL 16 — [Particionamento](https://www.postgresql.org/docs/16/ddl-partitioning.html)
- ADR 0022 — [Retenção e escalabilidade dos locks de slots](../adr/0022-retencao-e-escalabilidade-dos-slots.md)
- Spec vigente — [Core de booking](booking.md)
