# ADR 0001 — Stack Java 21 + Spring Boot 4 + Keycloak

Status: Accepted
Data: 2026-07-23

## Contexto

O Gnomon é a recriação do backend Moira (Python 3.12 + FastAPI) em uma nova stack, decidida
estratégicamente pelo time. A decisão de produto e as regras de domínio do Moira permanecem
válidas; apenas a plataforma técnica muda. O restante da stack (PostgreSQL, Redis, Docker
Compose, observabilidade) é preservado. Havia dúvida entre Spring Boot e Quarkus.

## Decisão

Stack principal do Gnomon:

- **Java 21 (LTS)** como linguagem. Java 25 LTS é opção de upgrade trivial de configuração.
- **Spring Boot 4** como framework web (concorrrente avaliado: Quarkus 3.37).
- **Maven** como build tool (wrapper `mvnw` commitado).
- **Spring Data JPA (Hibernate)** como ORM/data mapper.
- **Flyway** para migrations versionadas em SQL puro (`src/main/resources/db/migration`).
- **PostgreSQL** como banco relacional (preservado).
- **Keycloak** como IdP/OIDC provider; a API atua como **OAuth2 resource server**
  (`spring-boot-starter-oauth2-resource-server`). Autenticação sai da aplicação (ver ADR 0004).
- **Redis** para cache de leituras públicas, fail-open (preservado).
- **JUnit 5 + Mockito** para testes unitários; **Testcontainers + REST Assured** para
  integração (PostgreSQL, Redis e Keycloak efêmeros — equivalente ao `docker-compose.test.yaml`
  do Moira); **ArchUnit** para gates de arquitetura.
- **Spotless (google-java-format)** para formatação.
- **Docker Compose** para ambiente local (postgres, redis, keycloak) e observabilidade
  (Collector, Loki, Grafana — preservado).

Base package: `io.gnomon`. Coordenadas Maven: `io.gnomon:gnomon-api`.

## Por que Spring Boot e não Quarkus

- Ecossistema e mercado mais amplos; documentação e exemplos abundantes.
- Integração Keycloak consolidada via OAuth2 resource server, sem extensões específicas.
- Micrometer/OpenTelemetry maduros para a observabilidade vendor-neutral já adotada.
- Startup e memória do Quarkus não são críticos para este MVP (processo longo, deploy simples);
  o custo de APIs menos transferíveis do Quarkus não se paga.

## Consequências

- Todo código que toca banco passa por Spring Data JPA repositories; transações declarativas
  com `@Transactional` nos use cases da camada application.
- URLs JDBC usam o driver oficial PostgreSQL; Testcontainers governa os bancos de teste.
- A senha deixa de existir no domínio da aplicação: não há `password_hash`, política de senha
  ou endpoints de login próprios. O ADR 0012 do Moira é substituído pelo ADR 0004 do Gnomon.
- A aprendizagem e as convenções do ecossistema Spring (DI por construtor, configuração por
  `@ConfigurationProperties`, profiles `local`/`test`/`prod`) valem para todo o repositório.

## Rastreabilidade

- Substitui: Moira ADR 0001 (Stack Python/FastAPI) e ADR 0012 (política de senha).
- Preserva: livedoc seções 23–27 (config, Docker, observabilidade, CI, infra).
