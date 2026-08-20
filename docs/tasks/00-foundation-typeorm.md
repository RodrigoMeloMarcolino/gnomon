# Task 00 — Fundação executável e spike TypeORM

Status: done
Data de conclusão: 2026-08-19

## Entrega

- Node.js 24, pnpm, NestJS, TypeScript strict, ESLint, Prettier e Vitest;
- config Zod, CORS, request ID, logs JSON, erro `/v1`, health, readiness e OpenAPI;
- TypeORM com migrations explícitas, `synchronize: false` e `btree_gist`;
- spike PostgreSQL real para drift, idempotência, GiST, concorrência e escopo tenant;
- gate de imports/ciclos, Dockerfile, Compose, Keycloak local, Redis e OTLP opcional;
- GitHub Actions com gates equivalentes aos comandos locais.

## Evidências

| Gate                                  | Resultado                               |
| ------------------------------------- | --------------------------------------- |
| `pnpm format:check`                   | verde                                   |
| `pnpm lint`                           | verde                                   |
| `pnpm typecheck`                      | verde                                   |
| `pnpm architecture`                   | verde, 42 módulos sem violações         |
| `pnpm test`                           | verde, 14 testes                        |
| `pnpm test:e2e`                       | verde, 7 testes                         |
| `pnpm test:integration`               | verde, 6 testes em PostgreSQL 17 real   |
| `pnpm test:coverage`                  | verde, 90% statements / 85,71% branches |
| `pnpm build`                          | verde                                   |
| `docker compose config --quiet`       | verde                                   |
| `docker build -t gnomon:foundation .` | verde                                   |
| Compose migration/API/Keycloak smoke  | verde em projeto temporário isolado     |

## Riscos e pendências

- O snapshot OpenAPI do `gnomon-mock`, fixtures e suíte black-box continuam pendentes: a cópia
  local do repositório irmão contém somente documentação e não expõe o baseline executável.
- Redis e Keycloak estão preparados no ambiente local, mas somente serão integrados e validados
  por Testcontainers nas fases que implementam cache e identidade.
- O schema de negócio não faz parte desta task; a migration atual habilita apenas `btree_gist`.
