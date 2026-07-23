# Fase 00 — Fundação técnica

Status: done

## Objetivo

Repositório executável com Spring Boot 4, banco migrável e ambiente local reproduzível.

## Escopo

- Projeto Maven (`io.gnomon:gnomon-api`), Java 21, Spring Boot 4, wrapper `mvnw` commitado.
- Estrutura `io.gnomon` com módulos iniciais vazios (ADR 0002): `tenancy`, `catalog`,
  `availability`, `booking`, `customers`, `shared`.
- Dependências base: web, validation, data-jpa, postgresql, flyway, security,
  oauth2-resource-server, data-redis, micrometer, test (JUnit 5, Testcontainers, REST Assured).
- Flyway com `src/main/resources/db/migration` e migration inicial (`V1__extensions.sql`:
  pgcrypto + citext).
- `docker-compose.yaml`: `postgres` (16+), `redis` (7+), `keycloak` (26+, porta 8081, import
  de `keycloak/realm-gnomon.json` esquelético — refinado na fase 01).
- `GET /v1/health` (`{"status":"ok"}`) e `GET /v1/ready` (valida PostgreSQL).
- Config por profiles (`local`, `test`, `prod`) via `@ConfigurationProperties`; `.env.example`.
- Spotless (google-java-format) + `./mvnw spotless:check` verde.
- README de setup já existe na raiz (este repo).

## Fora de escopo

- Qualquer regra de negócio, segurança real, cache, observabilidade completa.

## Critérios de aceite

- [x] `./mvnw spring-boot:run` sobe contra os containers locais e aplica migrations.
- [x] `curl /v1/health` e `/v1/ready` respondem conforme README.
- [x] `./mvnw test` verde (smoke de contexto).
- [x] `./mvnw spotless:check` verde.

## Notas de implementação

Concluída em 2026-07-23. Validações executadas (JDK 21.0.11, Docker 29):

- `./mvnw spotless:check` ✅
- `./mvnw test` ✅ — 7 testes (3 `HealthControllerTest` unitários + 4 regras ArchUnit);
  smoke de contexto excluído por padrão via tag JUnit.
- `./mvnw verify -Pintegration` ✅ — 3 testes em `GnomonApplicationSmokeTest`
  (`@SpringBootTest` + Testcontainers PostgreSQL efêmero + REST Assured): health, readiness e
  extensões `pgcrypto`/`citext` aplicadas pela V1.
- `./mvnw spring-boot:run` contra os containers locais ✅ — `/v1/health` → `{"status":"ok"}`,
  `/v1/ready` → `{"status":"ready"}`.
- `docker compose --profile full up -d --build` ✅ — stack completa (postgres, redis,
  keycloak, api) só com Docker; realm `gnomon` importado pelo Keycloak; Flyway migra no startup.

Versões fixadas: Spring Boot **4.1.0**, Maven **3.9.16** (wrapper script-only, sem jar),
PostgreSQL **16-alpine**, Redis **7-alpine**, Keycloak **26.7.0**, Testcontainers **2.0.5**,
REST Assured **6.0.1**, ArchUnit **1.4.1**, Spotless **3.8.0** + google-java-format **1.35.0**.

Decisões e descobertas relevantes:

1. **Spring Boot 4 não gerencia mais Testcontainers nem REST Assured** — versões fixadas no
   `pom.xml` (BOM `testcontainers-bom` importado). Testcontainers 2.x mudou as coordenadas
   (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) e `PostgreSQLContainer` não é
   mais genérico e vive em `org.testcontainers.postgresql`.
2. **Autoconfigure do Flyway vive em módulo próprio no Boot 4** — dependência explícita
   `org.springframework.boot:spring-boot-flyway` (sem ela, as migrations silenciosamente não
   rodavam).
3. **Segurança temporária**: `SecurityConfig` `permitAll` (stateless, sem CSRF) e nenhum
   `issuer-uri` configurado — o resource server fica inerte. A fase 01 substitui pela chain da
   spec `keycloak-auth` (TODO marcado no código).
4. **Smoke de contexto nomeado `*Test`** (não `*IT`) para casar com o include padrão do
   surefire; a tag `integration` governa quando roda (`-Pintegration`/`-Pall-tests`).
   `archunit.properties` com `archRule.failOnEmptyShould=false` — regras vacuamente verdes
   enquanto os módulos estão vazios.
5. **Docker**: `Dockerfile` multi-stage (build `maven:3.9-eclipse-temurin-21`, runtime
   `eclipse-temurin:21-jre` não-root, cache BuildKit do `.m2`). Serviço `api` sob profile
   `full` para não alterar o fluxo dev do README. Portas do host parametrizáveis
   (`POSTGRES_PORT`, `REDIS_PORT`, `KEYCLOAK_PORT`, `API_PORT`) — nesta máquina, 5432/8080
   estavam ocupadas por outro projeto, então o `.env` local (gitignored) usa 5433/8082.
6. **`@ConfigurationProperties`**: fase 00 entrega o mecanismo (profiles `local`/`test`/`prod`,
   `@ConfigurationPropertiesScan`, binding por env vars, `.env.example`); a primeira classe
   tipada concreta chega na fase 01 com a config de segurança/CORS — sem knobs especulativos.

Riscos e follow-ups:

- **Issuer-uri no Docker (fase 01)**: o claim `iss` dos tokens emitidos via
  `localhost:8081/realms/gnomon` não bate com um issuer interno `http://keycloak:8081/...`;
  decidir na fase 01 (hostname único via `KC_HOSTNAME` ou override de validação).
- `docker-compose.observability.yaml` citado no README só existe a partir da fase 06.
- `keycloak/realm-gnomon.json` é esqueleto (clients `gnomon-web`/`gnomon-api`, usuário
  `dev@gnomon.local`/`dev`) — refinamento na fase 01 conforme spec.
- Actuator entrou como carrier do Micrometer (exposição web padrão mínima); contrato completo
  de observabilidade na fase 06 (ADR 0015).
