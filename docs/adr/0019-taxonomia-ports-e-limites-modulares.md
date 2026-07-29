# ADR 0019 — Taxonomia de ports e limites modulares verificáveis

Status: Accepted
Data: 2026-07-28

## Decisão

Cada módulo usa `application.port.in` para casos de uso, commands e results, `application.port.out`
para repositories e gateways e `application.service` para implementações Spring/transações.
`domain.model`, `domain.service` e `domain.exception` contêm somente regras puras. O pacote `api`
contém somente contrato HTTP e sua tradução.

Módulos estrangeiros só são consumidos por seus input ports. `shared.security` é o dono do
principal autenticado; `customers` é o dono do customer global. Implementações dependentes de
libphonenumber e SHA-256 são infraestrutura; contratos ficam no application.

## Consequências

`*Result` não é contrato HTTP e `*Response` não sai de `api`. Exceções de domínio não são
traduzidas diretamente por HTTP; a camada de aplicação publica códigos estáveis. ArchUnit protege
as direções de dependência e os pacotes canônicos. Esta decisão detalha e complementa o ADR 0002.
