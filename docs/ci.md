# CI — GitHub Actions

Pipeline de merge do Gnomon. Espelha o modelo do Moira (`lint`, `unit-tests`,
`integration-tests`, `pull-request-approval`) adaptado ao Maven/Testcontainers.

## Checks obrigatórios (merge na `main`)

### 1. `lint`

```bash
./mvnw -B spotless:check
```

Formatação com google-java-format via Spotless. Correção local: `./mvnw spotless:apply`.

### 2. `unit-tests`

```bash
./mvnw -B test
```

JUnit 5 sem Spring context pesado e sem containers; inclui os testes ArchUnit de arquitetura.

### 3. `integration-tests`

```bash
./mvnw -B verify -Pintegration
```

Testes marcados com a tag `integration`: `@SpringBootTest` + Testcontainers (PostgreSQL,
Redis, Keycloak efêmeros) + REST Assured. O runner do GitHub (`ubuntu-latest`) provê Docker;
nenhum serviço fixo no YAML — o Testcontainers governa o ciclo de vida e remove os containers
ao final. Não é necessário aplicar migrations manualmente: Flyway roda no startup do contexto
de teste.

### 4. `pull-request-approval`

Exige ≥ 1 approve de contribuidor (associação `OWNER`/`MEMBER`/`COLLABORATOR`/`CONTRIBUTOR`,
ignora bots e o autor do PR; review contra o SHA atual), com lista de isenção por autor.
Antes do approve, o único check que pode falhar é este.

## Proteção da branch `main`

- Exigir PR antes de merge.
- **Não** usar "Required approvals = 1" nativo (não permite exceção por autor) — a regra é o
  check `pull-request-approval`.
- Ativar "Require status checks" marcando os 4 checks como obrigatórios.

## Cache de build

- Cache do repositório Maven (`~/.m2/repository`) por chave de `pom.xml`.
- Cache de imagens Testcontainers não é configurado por padrão; pulls são feitos a cada run
  (imagens pequenas: postgres, redis, keycloak).

## Convenções

- Commits e PRs em português ou inglês, seguindo o histórico do repositório.
- Toda mudança arquitetural exige ADR no mesmo PR (ver AGENTS.md).
- Autor isento do approval gate: `RodrigoMeloMarcolino`.
