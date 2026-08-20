# ADR 0004 — Compatibilidade `/v1` com `gnomon-mock`

Status: Accepted
Data: 2026-08-19

## Decisão

O `gnomon-mock` é o baseline executável durante a reimplementação. O sucessor mantém
compatibilidade observável de método, path, query, headers, JWT/CORS, JSON/casing, status,
envelope/códigos de erro, idempotência, paginação e tempo.

Compatibilidade não exige o mesmo banco ou mecanismo interno. OpenAPI diff e uma suíte black-box
com fixtures determinísticos rodam contra as duas APIs. Divergências são classificadas; mudanças
incompatíveis exigem `/v2` ou migração explícita do Umbra.

O mock e o sucessor usam bancos separados e não executam dual write. Após o cutover, o sucessor
passa a publicar a evolução canônica do contrato.

## Consequências

- Umbra pode avançar contra o mock.
- O mock não é produção nem fallback automático.
- Drift comportamental é um defeito de release.
