# ADR 0004 — Keycloak: realm único + memberships locais

Status: Accepted
Data: 2026-07-23

## Contexto

A nova stack define Keycloak para autenticação. É preciso decidir como multi-tenancy se
relaciona com o IdP: realm por tenant, realm único com grupos, ou realm único com autorização
100% local. Também é preciso decidir como o usuário do Keycloak se materializa no banco.

## Decisão

1. **Realm único `gnomon`** para toda a plataforma. Tenant **não** é conceito do Keycloak.
2. **Autenticação 100% delegada**: login, registro self-service, recuperação de senha, MFA e
   social login são telas/fluxos do Keycloak. A API nunca recebe credenciais. Não existe
   endpoint de login/signup na API.
3. **API como OAuth2 resource server**: valida access tokens JWT emitidos pelo realm
   (`spring.security.oauth2.resourceserver.jwt.issuer-uri`). Algoritmo RS256 (assinatura
   assimétrica do realm), validação de issuer, audience e expiração.
4. **Autorização resolvida localmente**: `tenant_memberships` liga `users` ↔ `tenants` com
   roles `owner | admin | staff`. Nenhuma role de negócio vive no token; o token prova apenas
   identidade.
5. **Projeção local `users`**: tabela com `keycloak_sub` (subject do token, único), `email`
   (CITEXT, único) e `display_name`. Serve para FKs de integridade (memberships, collaborators)
   e listagens administrativas.
6. **JIT provisioning**: no primeiro request autenticado, um interceptor/filtro após a
   validação do JWT faz upsert da projeção local a partir das claims (`sub`, `email`, `name`).
7. **Clients do realm**: `gnomon-web` (público, authorization code + PKCE) para o frontend;
   a API não tem client próprio para login (apenas valida tokens). Client de machine-to-machine
   para jobs futuros fica fora do MVP.

## Consequências

- `docker-compose.yaml` inclui serviço `keycloak` (porta 8081) com import de realm
  (`keycloak/realm-gnomon.json`) para desenvolvimento; Testcontainers sobe Keycloak efêmero nos
  testes de integração de segurança.
- Logout e revogação são responsabilidade do Keycloak/frontend; a API trata tokens como
  stateless.
- Não há mais: `password_hash`, política de senha 8–64 (ADR 0012 Moira), emissão de JWT HS256
  (ADR 0013 Moira, parte auth). Segredos JWT da aplicação deixam de existir.
- Roles de negócio mudam sem precisar reemitir token (fonte: banco local).
- Config sensível: `KEYCLOAK_ISSUER_URI` por ambiente; em `local`, aponta para o container.

## Rastreabilidade

- Substitui: Moira ADR 0012 (política de senha) e a parte de autenticação do ADR 0013
  (JWT HS256 próprio).
- Preserva: a parte de ownership/idempotência do ADR 0013 (agora via memberships — ADR 0003 —
  e ADR 0014).
