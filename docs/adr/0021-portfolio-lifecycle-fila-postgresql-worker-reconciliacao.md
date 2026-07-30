# ADR 0021 — Lifecycle do portfólio, fila PostgreSQL, worker e reconciliação

Status: Accepted
Data: 2026-07-29

## Contexto

Imagens exigem validação, sanitização e remoção que não podem bloquear requisições HTTP nem deixar
artefatos parciais, quotas imprecisas ou estados irrecuperáveis após falhas.

## Decisão

`PortfolioImage` será aggregate root tenant-owned com os estados persistidos:

```
PENDING_UPLOAD → PROCESSING → AVAILABLE | FAILED → DELETING → DELETED
```

`UPLOADING` é somente estado do frontend; `UPLOADED` não persiste porque confirmação e criação do
job ocorrem na mesma transação. Disponibilidade técnica e publicação editorial são independentes:
somente `AVAILABLE` com `published_at` aparece publicamente. Há no máximo um destaque publicado
por tenant, garantido por índice parcial.

Jobs duráveis vivem em PostgreSQL e são processados por worker separado, no mesmo artefato Spring
com perfil próprio. Claims usam `FOR UPDATE SKIP LOCKED`, lease recuperável, concorrência inicial
um e no máximo três tentativas com backoff. O worker executa libvips fora do processo web, com
timeout e limites de CPU/memória. Ele aceita JPEG, PNG e WebP estáticos, valida bytes mágicos,
decodificação, dimensões e pixels, remove metadados e produz WebP privado `master` (até 4096 px),
`display` (1920 px) e `thumbnail` (480 px), sem upscale. A transação só publica keys depois que
todas as derivadas existirem.

Remover uma imagem a oculta imediatamente e muda o estado para `DELETING`; nenhum worker pode
promovê-la novamente. Remoção é idempotente e tombstones sem keys ficam 30 dias. Reconciliadores
periódicos tratam reservas abandonadas, callbacks ausentes, leases vencidos, parciais, remoções,
órfãos e drift de quotas.

## Consequências

O sistema tolera quedas e corridas processamento × remoção sem publicar conteúdo inconsistente.
Ele introduz tabelas de job/uso, worker, reconciliação e testes de concorrência PostgreSQL. Logs,
auditoria e métricas não podem conter URL assinada, filename, credenciais, conteúdo ou `tenantId`
como label.
