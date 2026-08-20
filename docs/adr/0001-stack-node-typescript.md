# ADR 0001 — Stack Node.js + TypeScript

Status: Accepted
Data: 2026-08-19

## Decisão

O sucessor usa Node.js em versão Active LTS, TypeScript strict e PostgreSQL. A fundação web deve
usar um framework modular com DI e OpenAPI; NestJS é a opção inicial. Keycloak, Redis,
Testcontainers, logs JSON e OpenTelemetry são preservados como capacidades.

O ORM é decidido separadamente pelo ADR 0002. Migrations SQL e recursos PostgreSQL avançados
permanecem explícitos, independentemente do ORM.

## Consequências

- Umbra e backend compartilham linguagem, mas não modelos internos.
- Validação de runtime continua obrigatória porque tipos TypeScript são apagados.
- Cargas CPU-bound não rodam no processo HTTP.
- Este ADR substitui a stack de produção do ADR Java 0001, sem alterar decisões de produto.
