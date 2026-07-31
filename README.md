# Gnomon

Backend do **Sun Catcher**, o SaaS de agendamento multi-tenant. `gnomon` é o nome do serviço
de API; o produto resultante da combinação gnomon + [umbra](../umbra) (frontend) é o Sun
Catcher. Ver a convenção completa no repo central de documentação
[ephemeris](../ephemeris).

O Sun Catcher é a recriação do [Moira](https://github.com/RodrigoMeloMarcolino/moira) em stack Java,
evoluindo o produto de provider único para **multi-tenancy com múltiplos calendários**: cada
tenant (negócio) possui um ou mais calendários, cada calendário pertence a um colaborador, e o
cliente final agenda com um calendário do tenant — sem precisar criar conta (guest booking).

O nome do serviço vem do gnômon: a peça do relógio de sol que projeta a sombra para medir as
horas.

## Estado atual

Checkpoint de continuidade: fases `00` a `05` estão concluídas; a próxima fase é `06` —
observabilidade. Além da fundação técnica, o código atual entrega autenticação JWT com
Keycloak, projeção JIT de usuários, tenants e memberships com autorização local, colaboradores,
calendários, offerings, atribuições e catálogo público.

Disponibilidade, booking e cache Redis já estão funcionais. A observabilidade está em implantação
conforme `docs/specs/structured-logging.md`.

## Stack

- Java 21 (LTS)
- Spring Boot 4
- PostgreSQL
- Spring Data JPA (Hibernate)
- Flyway
- Keycloak (autenticação OIDC; API como resource server)
- Redis (cache de leituras públicas, fail-open)
- Maven (wrapper `mvnw`)
- JUnit 5 + Testcontainers + REST Assured
- ArchUnit (gates de arquitetura)
- Spotless (google-java-format)
- Docker Compose
- Observabilidade vendor-neutral: logs JSON + OTLP (OpenTelemetry), Micrometer

## Setup local

Instale as ferramentas:

- JDK 21
- Git
- Docker Desktop

Suba PostgreSQL, Redis e Keycloak locais:

```powershell
docker compose up -d postgres redis keycloak
```

O Keycloak sobe em `http://localhost:8081` com o realm `gnomon` importado de
`keycloak/realm-gnomon.json` (admin local: `admin`/`admin`, apenas para desenvolvimento).

As migrations Flyway são aplicadas automaticamente no startup da API em `local`.

Suba a API:

```powershell
./mvnw spring-boot:run
```

### Rodar tudo com Docker (sem JDK local)

A stack completa — PostgreSQL, Redis, Keycloak **e a API** — roda apenas com Docker:

```powershell
docker compose --profile full up -d --build
```

A API sobe em `http://localhost:8080` depois que o PostgreSQL fica saudável, e as migrations
são aplicadas no startup. O build da imagem compila o projeto dentro do container (multi-stage),
sem exigir JDK ou Maven no host.

Se alguma porta do host estiver ocupada (5432, 6379, 8081, 8080), copie `.env.example` para
`.env` e sobrescreva `POSTGRES_PORT`, `REDIS_PORT`, `KEYCLOAK_PORT` ou `API_PORT`.

## Health checks

```powershell
curl http://127.0.0.1:8080/v1/health
curl http://127.0.0.1:8080/v1/ready
```

Respostas esperadas:

```json
{"status":"ok"}
```

```json
{"status":"ready"}
```

`/health` valida apenas que a aplicação está viva. `/ready` também valida a conexão com o
PostgreSQL.

## Autenticação e multi-tenancy

Implementado na fase `01`: a autenticação é delegada ao Keycloak (realm único
`gnomon`). A API valida access tokens JWT como OAuth2 resource server (`Authorization: Bearer
<access_token>`). Login, registro, recuperação de senha, MFA e social login são responsabilidade
do Keycloak — a API nunca vê senha.

`SecurityConfig` mantém apenas health, readiness e `/v1/public/**` anônimos. Rotas administrativas
exigem JWT válido; o filtro JIT cria ou atualiza a projeção local antes dos controllers.

A autorização administrativa é resolvida localmente: a tabela `users` é uma projeção local do
usuário do Keycloak (provisionada no primeiro request autenticado, via `keycloak_sub`), e
`tenant_memberships` liga usuário a tenant com role `owner`, `admin` ou `staff`.

Rotas administrativas carregam o tenant explicitamente no path:

- `POST /v1/tenants` (bootstrap: cria tenant + membership `owner` para o usuário autenticado)
- `GET /v1/tenants/{tenantSlug}/offerings`
- `POST /v1/tenants/{tenantSlug}/collaborators`
- `GET /v1/tenants/{tenantSlug}/appointments`

Rotas públicas para clientes finais ficam sob `/v1/public` e não exigem autenticação:

- `GET /v1/public/tenants/{slug}`
- `GET /v1/public/tenants/{slug}/calendars`
- `GET /v1/public/tenants/{slug}/offerings`
- `GET /v1/public/tenants/{slug}/available-slots?calendar_id=&offering_id=&date=`
- `POST /v1/public/tenants/{slug}/appointments`

## Contrato temporal de agendamento

`Calendar.timezone` deve ser um nome IANA válido, como `America/Fortaleza`, e governa o
calendário local do colaborador (com default herdado de `Tenant.timezone`).

Em `GET .../available-slots`, o parâmetro `date` representa a data local do calendário. A
resposta contém instantes canônicos em UTC com offset, por exemplo:

```json
["2027-07-01T12:00:00Z"]
```

Em `POST .../appointments`, `start_at` deve incluir offset de timezone. Exemplo:

```json
{
  "calendar_id": "00000000-0000-0000-0000-000000000000",
  "offering_id": "00000000-0000-0000-0000-000000000000",
  "start_at": "2027-07-01T09:00:00-03:00",
  "customer_name": "Customer Test",
  "customer_phone": "+155500000000"
}
```

O backend persiste appointments e slots ocupados como instantes UTC; regras de disponibilidade
semanal continuam sendo horas locais do calendário.

Erros HTTP seguem o envelope:

```json
{
  "error": {
    "code": "tenant_not_found",
    "message": "tenant not found",
    "details": null
  }
}
```

## Logging estruturado

O formatter JSON estruturado segue o contrato compatível com o OpenTelemetry Logs Data Model
(ver `docs/specs/structured-logging.md`). Toda resposta HTTP inclui `X-Request-ID` e
`X-Correlation-ID`; em `local` o console permanece legível por padrão.

Configuração principal:

```text
LOG_LEVEL=INFO
LOG_FORMAT=json
OTEL_SERVICE_NAME=gnomon
OTEL_EXPORTER_OTLP_LOGS_ENDPOINT=http://localhost:4318/v1/logs
```

### Pipeline local Collector, Loki e Grafana

Durante a fase `06`, a stack de observabilidade sobe separadamente:

```powershell
docker compose -f docker-compose.observability.yaml up -d
```

O Collector recebe OTLP em `localhost:4318`, o Loki responde em `localhost:3100` e o Grafana em
`http://localhost:3000` (`admin`/`admin` apenas para desenvolvimento).

## Qualidade

```powershell
./mvnw spotless:check
./mvnw test
```

## Testes

Rodar unitários:

```powershell
./mvnw test
```

Rodar integração (exige Docker — Testcontainers sobe PostgreSQL, Redis e Keycloak efêmeros):

```powershell
./mvnw verify -Pintegration
```

Rodar tudo:

```powershell
./mvnw verify -Pall-tests
```

Os testes de integração usam Testcontainers: nenhum container local de desenvolvimento é
reutilizado ou alterado, e os containers efêmeros são removidos ao final da suite.

## Flyway

As migrations versionadas ficam em `src/main/resources/db/migration` (SQL puro, `V<n>__<desc>.sql`).
Em `local` e `test` elas são aplicadas no startup; em demais ambientes, aplicar via pipeline:

```powershell
./mvnw flyway:migrate
```

## Fonte de verdade

As decisões de produto e arquitetura deste backend seguem o [PRD](docs/prd.md) e os
[Architecture Decision Records](docs/adr/README.md). O Gnomon herda as decisões de produto do
[livedoc do SaaS de Agendamento](https://docs.google.com/document/d/1JV_6vdwBUYo6V1Pj9qsVaRVxhXeZv_ZDWFQVpfXgdb4)
e dos ADRs do repositório Moira; cada ADR do Gnomon referencia explicitamente a decisão de
origem quando herdada.
