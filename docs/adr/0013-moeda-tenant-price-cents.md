# ADR 0013 — Moeda do tenant e preços em centavos

Status: Accepted
Data: 2026-07-23

## Contexto

Valores monetários não podem sofrer erro de precisão de float, e os serviços de um mesmo
negócio compartilham uma moeda. No modelo multi-tenant, o dono da moeda deixa de ser o provider
e passa a ser o tenant.

## Decisão

1. `tenants.currency_code` (ISO 4217, `CHAR(3)`, default **`BRL`**) define a moeda de todos os
   offerings do tenant.
2. `offerings.price_cents` é inteiro, opcional, `>= 0` quando informado. **Proibido float para
   dinheiro.**
3. O offering não carrega campo de moeda próprio; a moeda é herdada do tenant.
4. APIs convertem explicitamente entre valor de exibição e centavos inteiros.
5. Mudança de moeda do tenant com offerings existentes é operação restrita (regra futura).
6. Monetização do SaaS (planos, assinatura, comissão, checkout) é decisão separada e futura.

## Consequências

- `R$ 50,00` → `price_cents = 5000`, `tenants.currency_code = 'BRL'`.
- Serviços "sob consulta" usam `price_cents NULL`.

## Rastreabilidade

- Herda: Moira ADR 0011; livedoc 6.3.1. Mudança de escopo: `providers.currency_code` →
  `tenants.currency_code`.
