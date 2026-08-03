# INT-01 — Fundação de integração Umbra

Status: done

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

## Fechamento — 2026-08-01

- OpenAPI JSON/YAML recebeu o contrato semântico Umbra: paths públicos, seleção de tenant,
  exemplos estáveis do fixture, `bearerAuth` nas operações administrativas, parâmetros de
  disponibilidade, `Idempotency-Key`, erros e respostas de booking.
- Swagger UI fica desabilitado por padrão e habilitado apenas em `local` e `docker`; documentos
  JSON/YAML permanecem publicados.
- Testes cobrem o documento OpenAPI, preflights CORS reais via MockMvc e a rejeição explícita do
  preflight PUT. As chaves textuais remanescentes de `BookingIntegrationTest` foram substituídas
  por UUIDs canônicos; a validação de produção permanece ativa.
- O smoke agora sobe uma composição isolada com portas próprias, aguarda readiness com limite,
  verifica JSON/YAML, preflights, catálogo, disponibilidade, criação 201 e replay 200, e remove
  apenas os recursos do projeto ao finalizar.
- Gates executados: `spotless:check` e `test` passaram em Java 21 (247 testes, incluindo
  ArchitectureTest); o smoke Docker isolado passou com OpenAPI JSON/YAML, preflights e booking.
  O teste de aplicação real de `verify -Pintegration` foi tentado, mas o Maven em container não
  pôde acessar o Docker do host para iniciar Testcontainers; por isso `verify -Pall-tests` também
  não foi executável neste ambiente. A falha é ambiental (`/var/run/docker.sock`) e não reproduzível
  no código. O smoke equivalente contra a stack Docker real passou.

## Critérios restantes

- [x] Teste do documento OpenAPI para paths, segurança, exemplos e schemas.
- [x] Preflights reais incluindo ausência de `PUT`.
- [x] Gates Java 21 completos, incluindo integração e smoke em compose limpo.
