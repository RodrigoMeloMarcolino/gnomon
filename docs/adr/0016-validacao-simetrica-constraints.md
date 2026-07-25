# ADR 0016 — Validação simétrica e tradução determinística de constraints

Status: Accepted
Data: 2026-07-23

## Contexto

O Moira apresentou dois modos de falha simétricos e recorrentes:

1. **Validação assimétrica**: a escrita aceitava dados que a leitura não suportava. Exemplo
   canônico: `POST /v1/availability-rules` aceitava `start_time = 09:07` (validava só segundos,
   não o alinhamento de 15 min); na leitura, o cálculo de slots levantava exceção de boundary e
   **derrubava com 500** o endpoint público `available-slots` e o booking. O dado inválido foi
   persistido e o erro explodiu longe da causa.
2. **Violação de integridade virando 500**: `CHECK constraints` e outras violações conhecidas
   (ex.: `ck_start_before_end` via PATCH parcial que invertia horários) não tinham tradução
   mapeada e caíam no handler genérico como `internal_server_error`, em vez de um 4xx estável.

Ambos quebram o contrato de erros do ADR 0014 (códigos estáveis, envelope padrão) e destroem a
confiança do cliente na API. O Gnomon nasce depois dessas lições e precisa codificá-las como
regra, não como backlog.

## Decisão

1. **Validação simétrica**: todo invariante assumido por qualquer caminho de leitura/cálculo é
   validado na escrita (bean validation + domínio) **e** reforçado por `CHECK constraint` no
   banco. Nenhum dado que quebraria uma leitura futura pode ser persistido. Exemplos obrigatórios:
   `start_time`/`end_time` de `availability_rules` alinhados a boundaries de 15 minutos;
   `start_at` de booking com segundos/microssegundos zerados (não truncar — rejeitar com 422).
2. **Tradução determinística de constraints**: toda constraint nomeada
   (`UNIQUE`/`CHECK`/`FK`) criada em migration tem tradução explícita para um erro 4xx estável no
   `@RestControllerAdvice` (ex.: violação de `ck_start_before_end` → 422 `validation_error`;
   violação da unique de slot → 409 `slot_unavailable`). Violação de integridade conhecida
   **nunca** resulta em 500. Constraint sem tradução mapeada é bug de especificação.
3. **Tabela de tradução viva**: cada spec que define DDL inclui a tabela constraint → código de
   erro → status HTTP, e a fase 09 (gates de CI) verifica que toda constraint do schema tem
   entrada na tabela.

## Consequências

- Erros 4xx previsíveis para qualquer violação de integridade conhecida; 500 fica reservado a
  falhas genuinamente inesperadas.
- Validação na escrita encarece levemente os casos de uso de mutação, mas elimina a classe de
  bugs "dado inválido persistido derruba leitura pública".
- Specs de DDL ficam mais longas (tabela de tradução), em troca de contrato de erro completo.

## Rastreabilidade

- Origem: dívidas Moira — validação assimétrica de `availability_rules` (500 no endpoint público)
  e `CHECK constraints` sem handler (`internal_server_error`). Documentado na task
  `docs/tasks/00.5-hardening-licoes-moira.md`.
- Complementa: ADR 0014 (envelope e códigos estáveis), ADR 0010 (boundary de 15 min).
