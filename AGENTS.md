# AGENTS.md — Gnomon Node.js / Round-Trip SDD

Version: `1.0.0`

## Identidade

Este é o backend de produção sucessor do Sun Catcher. O repositório irmão `gnomon-mock`
preserva a implementação Java e o baseline executável da API `/v1`; `umbra` é o frontend
consumidor.

O projeto segue Round-Trip Specification-Driven Development: PRD, ADRs, tasks, testes e código
devem permanecer sincronizados. Antes de implementar, ler `docs/prd.md`, `docs/adr/README.md`,
`docs/tasks/README.md` e `docs/compatibility/v1-baseline.md`. Nenhuma decisão arquitetural muda
sem ADR.

## Regras não negociáveis

1. Compatibilidade `/v1` é comportamental: método, path, headers, auth/CORS, JSON, status,
   envelope/códigos de erro, paginação, idempotência e semântica temporal.
2. O mock e o sucessor usam bancos separados. Não implementar dual write.
3. Toda tabela tenant-owned possui `tenant_id`; escopo administrativo vem do path e de
   memberships locais, nunca de role de negócio no JWT.
4. Keycloak é dono de autenticação. A API não recebe nem armazena credenciais.
5. Customer é global por telefone canônico e não é User.
6. Booking usa uma transação curta e a exclusion constraint GiST em `appointments` como
   garantia final. Leitura de disponibilidade é apenas advisory.
7. Idempotência e conflito temporal são constraints independentes e produzem erros distintos.
8. Não existe `appointment_slots` no schema novo.
9. Dinheiro usa inteiros em minor units; instantes reais usam `TIMESTAMPTZ`; regras semanais
   usam horário local e timezone IANA explícita.
10. Logs JSON em stdout são obrigatórios; OTLP é opcional/fail-open; PII, tokens e chaves de
    idempotência não podem aparecer em logs.

## Arquitetura

Organizar por módulos de negócio:

```text
src/modules/<module>/
  api/
  application/
  domain/
  infrastructure/
```

Dependências permitidas: `api → application → domain ← infrastructure`. O domínio não importa
NestJS, ORM, HTTP, Redis ou SDKs. Entidades de persistência não são entidades de domínio.
Módulos estrangeiros são consumidos por input ports explícitos.

## Done criteria

Uma fatia só termina com comportamento especificado, testes unitários e de integração
proporcionais ao risco, compatibilidade verificada quando aplicável, migrations revisadas,
documentação sincronizada e riscos registrados. Booking, segurança e persistência exigem
PostgreSQL/Redis/Keycloak reais via Testcontainers.
