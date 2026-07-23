# Roadmap de implementação — Gnomon

Ordem de execução das fases. Cada fase só inicia com a anterior concluída (critérios de aceite
verdes). Status: `todo` | `doing` | `done`.

| Fase | Título | Status | Specs/ADRs de referência |
| ---- | ------ | ------ | ------------------------ |
| [00](00-fundacao.md) | Fundação técnica | done | ADR 0001, 0002 |
| [01](01-identidade-tenancy.md) | Identidade (Keycloak) e tenancy | todo | spec keycloak-auth, spec multi-tenancy, ADRs 0003–0005 |
| [02](02-catalogo.md) | Catálogo: colaboradores, calendários, offerings | todo | ADRs 0005–0007, 0012, 0013 |
| [03](03-disponibilidade.md) | Availability rules + available-slots | todo | spec booking, ADRs 0006, 0010 |
| [04](04-guest-booking.md) | Guest booking transacional | todo | spec booking, ADRs 0008–0012, 0014 |
| [05](05-cache-redis.md) | Cache Redis das leituras públicas | todo | ADR 0014, RNF-07 |
| [06](06-observabilidade.md) | Observabilidade (logs JSON + OTLP) | todo | spec structured-logging, ADR 0015 |
| [07](07-painel-admin.md) | Painel administrativo (appointments, customers) | todo | spec multi-tenancy, PRD 6.4 |
| [08](08-cancelamento-remarcacao.md) | Cancelamento e remarcação via token | todo | ADR 0008, livedoc 13–14 |
| [09](09-gates-ci.md) | Gates de CI e hardening | todo | docs/ci.md, ADR 0002 |

## Ordem e dependências

```
00 → 01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 09
```

- 05 (cache) pode ser antecipada se reads públicos pesarem; exige 03 e 04 estáveis.
- 06 (observabilidade) pode ser parcialmente antecipada (fundação de logging na fase 00),
  mas o contrato completo fecha na 06.
- 07 e 08 são independentes entre si, mas ambas exigem 04.

## Regras do roadmap

1. Uma fase por PR (ou fatia menor quando fizer sentido); critérios de aceite da task são o
   checklist do PR.
2. Nenhuma decisão arquitetural muda sem ADR (ver AGENTS.md).
3. Ao concluir uma fase: atualizar o status aqui, registrar notas de implementação e riscos na
   task correspondente, e verificar se o PRD continua verdadeiro.
4. O core de scheduling (fases 03 e 04) deve ter testes escritos junto da implementação —
  é o coração técnico do produto.
