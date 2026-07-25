# ADR 0018 — Frontend em repo separado (`umbra`) e stack web

Status: Accepted
Data: 2026-07-23

## Contexto

O PRD deixou o frontend como decisão em aberto (§13, item 1). O produto precisa de duas
superfícies web — booking público (SEO, baixa fricção, guest) e painel admin (OIDC,
interatividade densa) — e o frontend é também uma peça de portfólio de engenharia, o que pede
repo dedicado, stack atual e um calendário construído do zero.

## Decisão

1. **Repositório separado**: `umbra` (irmão deste repo), com workflow SDD próprio espelhado
   (AGENTS.md, `docs/{prd-frontend,adr,specs,tasks}`). Este repo permanece a fonte da verdade
   de domínio e contratos; o `umbra` consome.
2. **Stack**: Next.js 16 (App Router) + React 19 + TypeScript strict; Tailwind CSS v4 +
   shadcn/ui (Base UI); TanStack Query + nuqs; React Hook Form + Zod; Temporal via polyfill;
   oidc-client-ts (Auth Code + PKCE) contra o Keycloak; Vitest/Testing Library + MSW;
   detalhes e trade-offs nos ADRs 0001–0005 do `umbra`.
3. **Calendário 100% próprio** no frontend (ADR 0003 do `umbra`): core headless puro + views —
   nenhuma lib de agenda/calendário.
4. **Contratos estáveis são pré-requisito**: o front é desenvolvido primeiro contra MSW fiel
   ao PRD §9; quebras de contrato exigem ADR aqui antes de refletir no front.
5. **Dois consumidores de auth**: rotas públicas continuam sem autenticação (ADR 0008); rotas
   admin recebem Bearer JWT do fluxo OIDC do `umbra` — nenhum endpoint novo de auth é criado
   para o frontend.

## Consequências

- CI, deploy e versionamento independentes entre backend e frontend.
- CORS: a fase 01 do backend deve configurar origem permitida para o dev server do `umbra`
  (`http://localhost:3000`) — follow-up registrado na task 01 do backend.
- O envelope de erro (`error.code`) e a idempotência de booking (ADR 0014) passam a ter um
  consumidor real: códigos estáveis deixam de ser convenção e viram obrigação de compatibilidade.
- A decisão em aberto do PRD §13 (item 1) está fechada; PRD §8 e §13 emendados nesta data.

## Rastreabilidade

- Fecha: PRD §13 item 1 (frontend indefinido).
- Relaciona-se: ADR 0004 (Keycloak), ADR 0008 (guest booking), ADR 0014 (contrato de API).
- Espelho: ADRs 0001–0005 do repo `umbra`.
