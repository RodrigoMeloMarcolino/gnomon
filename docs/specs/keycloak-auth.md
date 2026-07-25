# Spec — Autenticação via Keycloak

Status: aprovada para implementação (emendada na task 00.5)
ADR relacionado: 0004
Substitui: Moira ADRs 0012 (senha) e 0013 (parte auth JWT HS256)

---

## 1. Objetivo

Remover toda gestão de credenciais da aplicação: autenticação delegada ao Keycloak (realm
único `gnomon`), API validando JWT como OAuth2 resource server, e provisionamento local do
usuário sob demanda.

## 2. Topologia do realm

Realm: `gnomon`

| Client | Tipo | Uso |
| ------ | ---- | --- |
| `gnomon-web` | público, standard flow + PKCE | frontend (login/registro) |
| `gnomon-api` | bearer-only (sem login) | validação de tokens pela API |

Configuração do realm:

- Self-registration habilitado (nome, e-mail, senha) com verificação de e-mail em `local`
  desabilitada e em demais ambientes habilitada.
- Política de senha gerenciada no Keycloak (mínimo 8, demais regras por ambiente).
- Temas/i18n pt-BR como follow-up.
- Social login (Google) como evolução futura — não no MVP.
- Tokens: access token TTL curto (5–15 min), refresh token com rotação (padrão do realm).

## 3. Configuração da API (resource server)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:http://localhost:8081/realms/gnomon}
          audiences: gnomon-api
```

Regras da `SecurityFilterChain`:

- `permitAll`: `/v1/public/**`, `/v1/health`, `/v1/ready`, erro.
- Qualquer outra rota: `authenticated` com JWT válido.
- Sessão stateless (`SessionCreationPolicy.STATELESS`); sem CSRF para a API REST; CORS somente
  com origens explicitamente configuradas (`CORS_ALLOWED_ORIGINS`, vazio = sem CORS).

Validações obrigatórias do token: assinatura (JWKS do realm), `exp`, `iss` exato, `aud`.

## 4. JIT provisioning (projeção local)

Filtro `OncePerRequestFilter` executado após a autenticação bem-sucedida:

1. Extrai `sub`, `email`, `name` das claims.
2. `SELECT ... FROM users WHERE keycloak_sub = :sub`.
3. Se não existir: insert com `email`/`display_name` das claims — **e-mail normalizado para
   lowercase antes de persistir** (Emenda 00.5; combinado com `CITEXT` na coluna, elimina a
   dívida Moira de contas duplicadas por case: `A@x.com` ≠ `a@x.com`).
4. Se existir e claims divergirem: update (email/display_name mais recentes, e-mail em
   lowercase).
5. Corrida de primeiro acesso simultâneo: conflito em `UNIQUE(keycloak_sub)` → re-ler e seguir
   (mesmo padrão de retry do customer no booking).
6. Disponibiliza o usuário local como principal para os controllers
   (`@AuthenticationPrincipal` customizado ou attribute de request).

Claims mínimas esperadas: `sub`, `email`, `name`. Token sem `email` verificado em ambiente
prod → `401` com código `email_not_verified` (config por ambiente; em `local`, tolerante).

## 5. Bootstrap do tenant

```
POST /v1/tenants
Authorization: Bearer <token>
{ "name": "Barbearia do João", "slug": "barbearia-do-joao", "timezone": "America/Fortaleza" }
```

1. Usuário local resolvido (JIT já executado).
2. Valida slug único global, formato lowercase, timezone IANA.
3. Transação: insert `tenants` + insert `tenant_memberships(role='owner')`.
4. Resposta `201` com o tenant. Conflito de slug → `409 tenant_slug_taken`.

`GET /v1/tenants` lista os tenants do usuário autenticado (via memberships).

## 6. Desenvolvimento local

- `docker compose up -d keycloak` sobe Keycloak 26 em `http://localhost:8081` com import de
  `keycloak/realm-gnomon.json` (admin console `admin`/`admin`, somente dev).
- O arquivo de realm é versionado no repositório; mudanças de configuração do realm passam por
  edição do JSON exportado, documentadas em PR.
- Usuário de teste de dev: `dev@gnomon.local` / senha definida no realm importado (apenas
  `local`).

## 7. Testes

Integração (Testcontainers Keycloak):

- Request sem token em rota admin → 401.
- Token expirado / issuer errado / audience errada → 401.
- Primeiro request com token válido → `users` provisionado (idempotente em requests
  subsequentes).
- Bootstrap de tenant cria membership owner; slug duplicado → 409.
- Rota pública acessível sem token.

Unit:

- Mapeamento de claims → projeção (divergências de e-mail/nome).
- Retry de corrida no primeiro acesso (mock de constraint violation).

## 8. Erros

| Código | HTTP | Quando |
| ------ | ---- | ------ |
| `unauthorized` | 401 | token ausente/inválido/expirado |
| `email_not_verified` | 401 | e-mail não verificado (ambientes estritos) |
| `tenant_slug_taken` | 409 | slug do tenant já em uso |
| `invalid_timezone` | 422 | timezone não IANA |

## 9. Critérios de aceite

- [ ] Nenhuma senha, hash de senha ou emissão de token na codebase.
- [ ] Resource server validando JWT do realm real.
- [ ] JIT provisioning idempotente e concorrência-seguro.
- [ ] Realm importável em `local` e reproduzível nos testes.
