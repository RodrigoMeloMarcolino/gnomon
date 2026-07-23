# ADR 0002 — Arquitetura modular pragmática (package by feature)

Status: Accepted
Data: 2026-07-23

## Contexto

O Moira adotou arquitetura por módulos de domínio com camadas (ADR 0010), inspirada em Clean /
Hexagonal Architecture e DDD tático, sem "DDD teatral". Essa decisão se mostrou correta e deve
ser preservada, adaptada ao idioma Java/Spring.

## Decisão

Organização por **módulos de negócio** sob o base package `io.gnomon`, cada um com quatro
subpacotes:

```
io.gnomon.<module>.api              # controllers REST, DTOs de request/response, exception mapping
io.gnomon.<module>.application      # use cases (orquestração, transações), ports, exceções de aplicação
io.gnomon.<module>.domain           # entidades de domínio, value objects, regras puras
io.gnomon.<module>.infrastructure   # entidades JPA, Spring Data repositories, adapters técnicos
```

Módulos iniciais: `tenancy` (tenants, memberships, users), `catalog` (collaborators, calendars,
offerings), `availability`, `booking` (appointments, slots), `customers`, `shared`.

Código transversal fica em `io.gnomon.shared` (config, security, observability, cache
primitives, erro envelope). Regras:

- Controllers são finos: validam entrada (`jakarta.validation`), chamam use cases, traduzem
  respostas. Sem regra de negócio.
- Use cases (`application`) orquestram e delimitam transações (`@Transactional`). Não conhecem
  HTTP nem JPA.
- `domain` não depende de Spring, JPA ou qualquer framework.
- Fronteiras só quando protegem domínio ou testabilidade; evitar abstração especulativa.
- Dependências permitidas: `api` → `application` → `domain` ← `infrastructure`
  (`infrastructure` também implementa ports de `application`). Enforced por testes ArchUnit.

## Consequências

- Novo módulo = novo package com as quatro camadas; não criar camadas globais genéricas
  (`controllers/`, `services/`, `repositories/` na raiz).
- Mapeamento JPA ↔ domínio acontece na borda de `infrastructure` quando o model ORM não puder
  ser o próprio modelo de domínio (o Moira aprendeu isso na task 04; o Gnomon já nasce com a
  fronteira prevista, mas sem proibir pragmatismo: entidades JPA simples podem ser usadas no
  domínio de módulos sem regra rica, documentando a exceção).
- `shared` não pode acumular regra de múltiplos domínios (guardrail herdado do checkpoint
  2026-06-23 do Moira).

## Rastreabilidade

- Herda: Moira ADR 0010 (arquitetura modular pragmática); livedoc seções 4 e 5.
