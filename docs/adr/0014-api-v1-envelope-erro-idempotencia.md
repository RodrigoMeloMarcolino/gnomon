# ADR 0014 — Rotas `/v1`, envelope de erro e idempotência de booking

Status: Accepted
Data: 2026-07-23

## Contexto

Contratos HTTP estáveis permitem evoluir o backend sem quebrar clientes, e o booking público
precisa tolerar retries de rede sem duplicar agendamento. O Moira consolidou: API somente sob
`/v1`, separação público/admin, envelope de erro padrão e `Idempotency-Key` no booking.

## Decisão

1. **API somente sob `/v1`**. Rotas públicas sob `/v1/public/tenants/{slug}/...`, sem
   autenticação. Rotas administrativas autenticadas sob `/v1/tenants/{tenantSlug}/...`, com o
   tenant explícito no path (ADR 0003) e membership validada pelo use case.
2. **Envelope de erro padrão** em todas as respostas de erro:

   ```json
   {"error": {"code": "tenant_not_found", "message": "tenant not found", "details": null}}
   ```

   Erros de validação usam `code = "validation_error"` com `details` por campo. Códigos são
   estáveis e não derivados da mensagem. Tradução de exceções de aplicação para HTTP é
   centralizada (`@RestControllerAdvice`), sem `try/catch` repetitivo em controllers.
3. **Idempotência no booking público**: header `Idempotency-Key` opcional. Mesma chave + mesmo
   payload (fingerprint hash) → replay do appointment original com a mesma resposta. Mesma
   chave + payload diferente → conflito (`409`). Unicidade por tenant:
   `UNIQUE(tenant_id, idempotency_key)` em `appointments` (escopo sobe de provider para tenant).
4. Respostas públicas nunca expõem `user_id`, dados de conta de colaboradores ou e-mails de
   membros.
5. Health: `/v1/health` (vivo) e `/v1/ready` (valida PostgreSQL) fora do escopo de auth.

## Consequências

- Retry seguro no submit do booking (duplo clique, timeout de rede, refresh).
- Consumers podem confiar em códigos de erro estáveis para lógica de cliente.
- Versionamento futuro (`/v2`) segue o mesmo padrão de prefixo.

## Rastreabilidade

- Herda: Moira ADRs 0013 (parte idempotência) e 0014; livedoc seções 19–20.
- Mudanças de escopo: paths ganham `tenants/{tenantSlug}`; idempotência sobe para o tenant.
