# Fase 09 — Gates de CI e hardening

Status: todo

## Objetivo

Qualidade automatizada no nível do Moira: gates de arquitetura, cobertura, schema drift e
smoke E2E, com CI verde e branch protegida.

## Escopo

- Testes ArchUnit: direção de dependências por módulo (`api` → `application` → `domain` ←
  `infrastructure`), domínio sem Spring/JPA, controllers finos (sem chamada direta a
  repository), `shared` sem regra de módulo.
- Verificação de schema drift: Flyway migrations vs entidades JPA
  (`hbm2ddl` validate em perfil de teste ou task dedicada no CI).
- Cobertura com baseline (JaCoCo) falhando abaixo do piso acordado.
- Smoke E2E: bootstrap tenant → colaborador → offering → atribuição → availability →
  available-slots → booking → tentativa de conflito (profile dedicado).
- GitHub Actions conforme `docs/ci.md`: jobs lint/unit/integration/approval-gate; proteção da
  `main` com os 4 checks obrigatórios.
- Índices de FK guiados por `EXPLAIN` nas consultas quentes (appointments por calendário/data,
  memberships por usuário).

## Fora de escopo

- Deploy/CD para ambiente real; performance tests formais; mutation testing.

## Testes

- Os próprios gates são os testes; smoke E2E roda no CI contra Testcontainers.

## Critérios de aceite

- [ ] PR só mergeia com os 4 checks verdes.
- [ ] Violação proposital de arquitetura quebra o build (prova do gate).

## Notas de implementação

(preencher ao concluir)
