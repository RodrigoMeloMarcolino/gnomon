# Architecture Decision Records — Gnomon

Índice dos ADRs do backend Gnomon. Cada ADR referencia a decisão de origem no Moira/livedoc
quando herdada.

| ADR | Título | Status | Origem Moira |
| --- | ------ | ------ | ------------ |
| [0001](0001-stack-java-spring-boot.md) | Stack Java 21 + Spring Boot 4 + Keycloak | Accepted | Substitui ADR 0001 |
| [0002](0002-arquitetura-modular-pragmatica.md) | Arquitetura modular pragmática (package by feature) | Accepted | ADR 0010 |
| [0003](0003-multi-tenancy-shared-schema.md) | Multi-tenancy: shared schema, tenant acima de calendários | Accepted | Checkpoint 37.12 (blueprint) |
| [0004](0004-keycloak-realm-unico-memberships-locais.md) | Keycloak: realm único + memberships locais | Accepted | Substitui ADRs 0012/0013 (parte auth) |
| [0005](0005-colaborador-entidade-login-opcional.md) | Colaborador como entidade com login opcional | Accepted | Nova |
| [0006](0006-calendario-dono-da-agenda.md) | Calendário como dono da agenda | Accepted | Nova |
| [0007](0007-servicos-tenant-atribuicao-calendario.md) | Serviços no tenant com atribuição por calendário | Accepted | Nova |
| [0008](0008-guest-booking-sem-conta.md) | Guest booking sem conta obrigatória | Accepted | ADR 0002 |
| [0009](0009-customer-global-telefone-canonico.md) | Customer global por telefone canônico | Accepted | ADRs 0003/0004 |
| [0010](0010-slots-15min-nao-persistir-disponiveis.md) | Slots de 15 min; não persistir slots disponíveis | Accepted | ADRs 0005/0006 |
| [0011](0011-double-booking-constraint-calendario.md) | Double booking via `UNIQUE(calendar_id, slot_start_at)` | Accepted | ADR 0007 |
| [0012](0012-snapshot-duracao-multiplo-15.md) | Snapshot de duração; durações múltiplas de 15 | Accepted | ADRs 0008/0009 |
| [0013](0013-moeda-tenant-price-cents.md) | Moeda do tenant e preços em centavos | Accepted | ADR 0011 |
| [0014](0014-api-v1-envelope-erro-idempotencia.md) | Rotas `/v1`, envelope de erro e idempotência de booking | Accepted | ADRs 0013/0014 |
| [0015](0015-observabilidade-vendor-neutral.md) | Logging estruturado vendor-neutral + OTLP | Accepted | ADR 0015 |
| [0016](0016-validacao-simetrica-constraints.md) | Validação simétrica e tradução determinística de constraints | Accepted | Nova (dívida Moira, task 00.5) |
| [0017](0017-disciplina-schema-ddl.md) | Disciplina de schema: índices de FK, `updated_at` real, status tipados, zero colunas mortas | Accepted | Nova (dívida Moira, task 00.5) |
| [0018](0018-frontend-umbra-repo-stack.md) | Frontend em repo separado (`umbra`) e stack web | Accepted | Nova |
| [0019](0019-taxonomia-ports-e-limites-modulares.md) | Taxonomia de ports e limites modulares verificáveis | Accepted | Nova |

## Convenção

- Status possíveis: `Proposed`, `Accepted`, `Deprecated`, `Superseded by ADR-XXXX`.
- Nenhuma decisão arquitetural muda sem ADR novo ou atualização explícita do ADR existente.
- ADRs herdados do Moira mantêm as mesmas consequências de produto, salvo indicação contrária.
