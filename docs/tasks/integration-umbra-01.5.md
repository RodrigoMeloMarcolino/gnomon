# INT-01 — Fundação de integração Umbra

Status: doing

## Escopo

- Springdoc gerado pelo código em `/v3/api-docs` e `/v3/api-docs.yaml`; UI em local/docker.
- Contratos de seleção públicos para Umbra em `snake_case`; administração permanece compatível.
- `Idempotency-Key` UUID canônico, CORS sem `PUT` e callbacks OIDC locais exatos.
- Fixture externa ao Flyway e smoke determinístico para o tenant `umbra-smoke`.

## Progresso

- OpenAPI, UI por profile, DTOs públicos e validação UUID implementados.
- Realm usa PKCE S256, audience `gnomon-api`, callbacks `/auth/callback` e
  `/auth/silent-callback`, origem `http://localhost:3000` e usuário dev de subject fixo.
- `scripts/fixtures/umbra-smoke.sql` e `scripts/smoke-umbra.sh` criam e exercitam o cenário
  determinístico; o serviço `smoke-seed` é one-shot no profile `smoke`.

## Critérios restantes

- [ ] Teste do documento OpenAPI para paths, segurança, exemplos e schemas.
- [ ] Preflights reais incluindo ausência de `PUT`.
- [ ] Gates Java 21 completos, incluindo integração e smoke em compose limpo.
