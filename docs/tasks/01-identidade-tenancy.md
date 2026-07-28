# Fase 01 — Identidade (Keycloak) e tenancy

Status: todo

## Objetivo

Autenticação delegada ao Keycloak funcionando end-to-end, com projeção local de usuários,
bootstrap de tenant e memberships com autorização por role.

## Escopo

- `keycloak/realm-gnomon.json` completo (realm, clients `gnomon-web`/`gnomon-api`,
  self-registration, usuário dev) conforme `docs/specs/keycloak-auth.md`.
- Issuer público + JWK Set URI interno no Docker e audience mapper `gnomon-api` no client web.
- `SecurityFilterChain`: público `/v1/public/**` + health; restante autenticado; stateless.
- Migrations: `users`, `tenants`, `tenant_memberships` (spec multi-tenancy 3.1–3.3).
- Filtro de JIT provisioning (upsert `users` por `keycloak_sub`, retry de corrida).
- Use cases: `CreateTenantUseCase` (bootstrap, membership owner), `ListMyTenantsUseCase`,
  `GetTenantUseCase`, gestão de memberships (add/remove/change role, invariante último owner).
- A role `staff` não pode ser criada diretamente nesta fase: sem colaborador vinculado,
  retorna `422 staff_requires_collaborator`; o vínculo atômico nasce na fase 02.
- Endpoints: `POST /v1/tenants`, `GET /v1/tenants`, `GET/PATCH /v1/tenants/{tenantSlug}`,
  `GET/POST/DELETE /v1/tenants/{tenantSlug}/memberships[...]`.
- Resolução de escopo: validação membership+role por operação; erros da spec
  (`tenant_not_found`, `membership_required`, `insufficient_role`, `last_owner`).
- Tradução central de exceções (`@RestControllerAdvice`) com envelope padrão (ADR 0014).
- CORS para o dev server do frontend `umbra` (`http://localhost:3000`) nas rotas `/v1/**`
  (ADR 0018); client Keycloak `gnomon-web` configurado para o fluxo Auth Code + PKCE do
  `umbra` (redirect URIs de dev).

## Fora de escopo

- Convites por e-mail; social login; MFA; colaboradores/calendários (fase 02).

## Testes

- Integração com Testcontainers Keycloak: 401 sem token, 401 token inválido, JIT idempotente,
  bootstrap cria owner, slug duplicado 409, último owner 409.
- Cross-tenant: membro do tenant A não lê/edita tenant B.
- Unit: matriz de roles, invariantes de membership.

## Critérios de aceite

- [ ] Critérios das specs keycloak-auth (seção 9) e multi-tenancy (seção 9, itens de identidade).
- [ ] Zero código de senha/token próprio na codebase.

## Notas de implementação

(preencher ao concluir)
