# ADR 0003 — Arquitetura modular e hexagonal

Status: Accepted
Data: 2026-08-19

## Decisão

Organizar por feature (`tenancy`, `catalog`, `availability`, `booking`, `customers`, `shared`),
cada uma com `api`, `application`, `domain` e `infrastructure`.

Dependências permitidas: `api → application → domain ← infrastructure`. Controllers validam e
traduzem HTTP; casos de uso orquestram transações; domínio contém regras puras; adapters
implementam ports. Módulos estrangeiros são consumidos por input ports.

## Consequências

- Entidades TypeORM/Prisma nunca são modelos de domínio.
- DTO HTTP não atravessa a camada application.
- Gates de imports e ciclos substituem o ArchUnit do mock.
- A arquitetura preserva a intenção dos ADRs Java 0002 e 0019 sem importar Spring/JPA.
