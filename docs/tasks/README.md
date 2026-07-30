# Roadmap de implementação — Gnomon

Checkpoint operacional para retomada entre sessões:
[implementation-checkpoint.md](implementation-checkpoint.md).

Ordem de execução das fases. Cada fase só inicia com a anterior concluída (critérios de aceite
verdes). Status: `todo` | `doing` | `done`.

| Fase | Título | Status | Specs/ADRs de referência |
| ---- | ------ | ------ | ------------------------ |
| [00](00-fundacao.md) | Fundação técnica | done | ADR 0001, 0002 |
| [00.5](00.5-hardening-licoes-moira.md) | Hardening de nascimento: lições do Moira (docs-only) | done | ADRs 0014 (emenda), 0016, 0017 |
| [00.6](00.6-codex-handoff.md) | Handoff para Codex (docs-only) | done | PRD, ADR index, roadmap |
| [01](01-identidade-tenancy.md) | Identidade (Keycloak) e tenancy | done | spec keycloak-auth, spec multi-tenancy, ADRs 0003–0005 |
| [02](02-catalogo.md) | Catálogo: colaboradores, calendários, offerings | done | ADRs 0005–0007, 0012, 0013 |
| [03](03-disponibilidade.md) | Availability rules + available-slots | done | spec booking, ADRs 0006, 0010, 0016 |
| [04](04-guest-booking.md) | Guest booking transacional | done | spec booking, ADRs 0008–0012, 0014, 0016, 0017 |
| [04.5](04.5-hardening-arquitetural.md) | Hardening arquitetural pré-fase 05 | done | ADRs 0002, 0019 |
| [05](05-cache-redis.md) | Cache Redis das leituras públicas | doing | ADR 0014, RNF-07 |
| [06](06-observabilidade.md) | Observabilidade (logs JSON + OTLP) | todo | spec structured-logging, ADR 0015 |
| [07](07-painel-admin.md) | Painel administrativo (appointments, customers) | todo | spec multi-tenancy, PRD 6.4 |
| [08](08-cancelamento-remarcacao.md) | Cancelamento e remarcação via token | todo | ADR 0008, livedoc 13–14 |
| [07.5](07.5-tenant-portfolio.md) | Portfólio multi-tenant (workstream paralelo) | todo | ADRs 0020–0021, feature tenant-portfolio |
| [09](09-gates-ci.md) | Gates de CI e hardening | todo | docs/ci.md, ADR 0002 |

## Ordem e dependências

```
00 → 00.5 → 00.6 → 01 → 02 → 03 → 04 → 04.5
                                              ├→ 05 cache
                                      ├→ 06 observabilidade
                                      ├→ 07 admin
                                      ├→ 07.5 portfólio
                                      └→ 08 cancel/remarcação
05 + 06 + 07 + 07.5 + 08 → 09
```

- 00.5 (hardening docs-only) é bloqueante da 01: emenda specs/ADRs/tasks com as lições do
  Moira antes de qualquer código de negócio.
- 00.6 é o checkpoint de troca de agente para Codex; não altera decisões de produto nem
  arquitetura.
- 05, 06, 07, 07.5 e 08 são workstreams paralelizáveis após o core da fase 04 estabilizar.
- 07.5 é bloqueante da fase 09, mas não bloqueia os demais workstreams; a etapa atual é somente
  documental e reserva a próxima migration V6.
- 06 pode ter fundação antecipada, mas o contrato completo fecha na própria fase.
- 09 integra e valida os quatro workstreams pós-core.

## Regras do roadmap

1. Uma fase por PR (ou fatia menor quando fizer sentido); critérios de aceite da task são o
   checklist do PR.
2. Nenhuma decisão arquitetural muda sem ADR (ver AGENTS.md).
3. Ao concluir uma fase: atualizar o status aqui, registrar notas de implementação e riscos na
   task correspondente, e verificar se o PRD continua verdadeiro.
4. O core de scheduling (fases 03 e 04) deve ter testes escritos junto da implementação —
  é o coração técnico do produto.
