# ADR 0003 — Multi-tenancy: shared schema, tenant acima de calendários

Status: Accepted
Data: 2026-07-23

## Contexto

O Moira operava com um tenant implícito centrado em `provider`. O checkpoint 37.12 do livedoc
registrou o blueprint de multi-tenancy: **tenant acima de provider**, shared schema com
`tenant_id` explícito, `users` globais, `tenants` + `tenant_memberships` como núcleo da conta,
e `customers` globais preservados. O Gnomon implementa esse blueprint desde a fundação, com a
evolução de que o "provider" se torna **calendário de colaborador** (ADR 0006).

Alternativas avaliadas: schema-per-tenant e database-per-tenant (isolation maior, custo
operacional incompatível com o MVP); tenant implícito (não atende múltiplos calendários).

## Decisão

1. **Shared schema, shared database**: todas as tabelas tenant-owned carregam `tenant_id`
   (UUID, FK `tenants.id`), mesmo quando outra FK de negócio (ex.: `calendar_id`) já existe.
2. **Tenant acima de calendários**: `tenants` é a conta/negócio; `calendars` vivem dentro do
   tenant. O tenant nunca substitui o calendário como dono da agenda (ADR 0006).
3. **`users` global**: identidade é global (projeção do Keycloak); o vínculo usuário ↔ tenant é
   `tenant_memberships(tenant_id, user_id, role)` com `UNIQUE(tenant_id, user_id)` e
   `role IN ('owner','admin','staff')`.
4. **`customers` global** (ADR 0009): sem `tenant_id`; relação inferida via appointments.
5. **Seleção explícita de escopo**: rotas administrativas carregam o tenant no path
   (`/v1/tenants/{tenantSlug}/...`). Um usuário pode ter múltiplos tenants; não existe "tenant
   inferido do token". O use case valida membership + role para o tenant do path antes de
   qualquer operação.
6. **Isolamento obrigatório**: todo repository/use case administrativo filtra por `tenant_id`.
   Recursos de outro tenant respondem `403` quando autenticado e inexistentes para o caller;
   recursos públicos inexistentes respondem `404`.

## Consequências

- Toda migration de tabela tenant-owned inclui `tenant_id NOT NULL REFERENCES tenants(id)` e
  índice adequado; constraints únicas de negócio incluem `tenant_id` (ex.:
  `UNIQUE(tenant_id, idempotency_key)`, `UNIQUE(tenant_id, slug)` em calendars).
- Testes de integração devem incluir casos explícitos de vazamento cross-tenant (tenant A não
  lê/edita nada do tenant B).
- Futura camada de agentes, knowledge base, integrações e API keys deve nascer tenant-aware.
- `ON DELETE`: tenant → filhos administrativos em cascade é decisão adiada para a fase de
  hardening (registrar em ADR futuro se mudar); appointments e customers globais nunca são
  removidos em cascade por tenant.

## Rastreabilidade

- Implementa: livedoc checkpoint 37.12 (blueprint de multi-tenancy) e seção 27 (BaaS vertical:
  "todo acesso a dados filtrado por tenant_id").
- Relacionados: ADRs 0004, 0005, 0006, 0009, 0014.
