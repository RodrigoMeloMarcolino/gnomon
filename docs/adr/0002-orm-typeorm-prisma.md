# ADR 0002 — ORM: TypeORM versus Prisma

Status: Accepted
Data: 2026-08-19

## Contexto

O domínio combina CRUD convencional com PostgreSQL avançado: upsert concorrente, constraints
nomeadas, FKs compostas, índices parciais, ranges, GiST e transações curtas. O ORM não pode ser a
autoridade exclusiva do schema nem vazar para o domínio.

## Decisão

Adotar **TypeORM 1.1** com o driver `pg`, `synchronize: false`, `migrationsRun: false`, entidades
apenas em `infrastructure` e SQL explícito nos caminhos críticos. Migrations são executadas por um
runner único e separado do processo HTTP.

Prisma deixa de ser alternativa ativa. A escolha explícita do projeto por TypeORM foi submetida a
um spike real em PostgreSQL 17 antes desta aceitação.

## Evidências dos gates

O teste `test/integration/typeorm-spike.spec.ts`, executado via Testcontainers, comprovou:

- aplicação versionada da migration `btree_gist` e ausência de migrations ou drift pendentes;
- insert, replay e payload divergente dentro de transações curtas;
- identificação determinística da unique `23505` e da exclusion constraint `23P01`;
- concorrência por conexões independentes, com um único vencedor para intervalos sobrepostos;
- intervalos consecutivos permitidos por `[start_at,end_at)`;
- consulta de ranges paginada e explicitamente tenant-scoped;
- domínio e application sem decorators ou imports do ORM.

Versões verificadas: Node.js 24.11, TypeORM 1.1.0, `pg` 8.23.0 e PostgreSQL 17.

## Limitações registradas

- Exclusion constraints, ranges, índices parciais e SQL de concorrência não são modelados como
  abstrações de domínio do TypeORM; permanecem SQL revisado em migrations/adapters.
- A tradução de erros depende de `driverError.code` e `driverError.constraint`, expostos pelo
  driver PostgreSQL e filtrados antes de subir para a aplicação.
- `schema:sync`, `synchronize` e migrations automáticas no startup são proibidos em todos os
  ambientes.

## Consequências

- Repositórios TypeORM e entidades de persistência não atravessam os ports do domínio.
- Transações críticas usam `DataSource`/`EntityManager` explicitamente.
- Toda evolução avançada de PostgreSQL exige migration SQL e teste em PostgreSQL real.
