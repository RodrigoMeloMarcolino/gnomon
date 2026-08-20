# Gnomon

Backend de produção do **Sun Catcher**, em Node.js e TypeScript, com compatibilidade
comportamental com a API `/v1` preservada no `gnomon-mock`. O frontend consumidor vive no
repositório irmão `umbra`.

## Estado atual

A fundação executável está pronta: NestJS 11, TypeORM 1.1, PostgreSQL 17, configuração validada,
logs JSON seguros, health/readiness, OpenAPI, migrations, testes, gate arquitetural, Docker e CI.
Ainda não existem tabelas nem regras de negócio. O snapshot OpenAPI do mock continua pendente
porque a cópia local de `gnomon-mock` não contém uma aplicação executável.

## Requisitos

- Node.js 24 LTS (`.nvmrc`);
- pnpm 11 via Corepack;
- Docker com Compose para PostgreSQL, Redis, Keycloak e testes de integração.

## Primeira execução

```bash
corepack enable
corepack prepare pnpm@11.22.0 --activate
pnpm install --frozen-lockfile
pnpm local:setup
pnpm local:up
pnpm migration:run
pnpm start:dev
```

`pnpm local:setup` cria um `.env` ignorado pelo Git, com credenciais locais aleatórias e permissão
`0600`. O comando não sobrescreve um arquivo existente. A aplicação fica disponível em
`http://localhost:8080`.

As portas podem ser alteradas no `.env` (`POSTGRES_PORT`, `REDIS_PORT` e `KEYCLOAK_PORT`) quando
já estiverem ocupadas. O volume PostgreSQL é versionado como `postgres17-data` para impedir que um
volume antigo seja aberto acidentalmente por outro major do servidor.

Para executar aplicação e migration runner também em containers:

```bash
docker compose --profile app up --build -d
```

O profile opcional de observabilidade inicia um OTLP Collector com exporter `debug`:

```bash
docker compose --profile observability up -d otel-collector
```

Defina `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces` para exportar spans. A ausência
ou falha do OTLP não impede o runtime da API.

## Endpoints da fundação

| Método | Path                | Finalidade                             |
| ------ | ------------------- | -------------------------------------- |
| `GET`  | `/v1/health`        | liveness do processo                   |
| `GET`  | `/v1/ready`         | readiness com `SELECT 1` no PostgreSQL |
| `GET`  | `/v3/api-docs`      | OpenAPI JSON                           |
| `GET`  | `/v3/api-docs.yaml` | OpenAPI YAML                           |
| `GET`  | `/docs`             | Swagger UI, desabilitado em produção   |

Erros HTTP usam o envelope `{ "error": { "code", "message", "details" } }`. Logs HTTP registram
somente request ID, método, status e duração; headers, bodies, query strings, tokens, cookies, PII
e chaves de idempotência não são serializados.

## Comandos

| Comando                     | Uso                              |
| --------------------------- | -------------------------------- |
| `pnpm start:dev`            | runtime com reload               |
| `pnpm build` / `pnpm start` | build e execução de produção     |
| `pnpm format:check`         | formatação sem reescrita         |
| `pnpm lint`                 | ESLint tipado                    |
| `pnpm typecheck`            | TypeScript strict                |
| `pnpm architecture`         | ciclos e fronteiras hexagonais   |
| `pnpm test`                 | testes unitários                 |
| `pnpm test:e2e`             | contrato HTTP da fundação        |
| `pnpm test:integration`     | spike TypeORM em PostgreSQL real |
| `pnpm test:coverage`        | cobertura unitária + HTTP        |
| `pnpm validate`             | gates locais sem Docker          |

Migrations nunca são aplicadas automaticamente no startup e `synchronize` permanece desabilitado:

```bash
pnpm migration:create src/database/migrations/NomeDaMigration
pnpm migration:generate src/database/migrations/NomeDaMigration
pnpm migration:show
pnpm migration:run
pnpm migration:revert
```

Um único runner de deploy deve executar `migration:run` antes de liberar as instâncias da API.

## Arquitetura

Features seguem a estrutura:

```text
src/modules/<module>/
  api/
  application/
  domain/
  infrastructure/
```

O gate automatiza `api → application → domain ← infrastructure`, proíbe ciclos e mantém o domínio
sem NestJS, TypeORM ou outros SDKs. Entidades TypeORM pertencem exclusivamente a
`infrastructure`; migrations SQL explícitas continuam sendo a autoridade do schema.

Documentos de referência:

- [PRD](docs/prd.md)
- [ADRs](docs/adr/README.md)
- [Roadmap](docs/tasks/README.md)
- [Fundação e spike TypeORM](docs/tasks/00-foundation-typeorm.md)
- [Baseline de compatibilidade](docs/compatibility/v1-baseline.md)
