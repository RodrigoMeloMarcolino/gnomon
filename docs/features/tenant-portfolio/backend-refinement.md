# Refinamento backend — Portfólio multi-tenant

Status: congelado para implementação futura (docs-only, 2026-07-29)

## Domínio, persistência e quotas

`PortfolioImage` é aggregate root tenant-owned: `PENDING_UPLOAD → PROCESSING → AVAILABLE | FAILED → DELETING → DELETED`. `UPLOADING` é estado somente do frontend e `UPLOADED` não é persistido. Uma imagem pública é `AVAILABLE` com `published_at`; `alt_text` é obrigatório, `caption` opcional e um índice parcial garante no máximo um destaque publicado por tenant.

`V6__tenant_portfolio.sql` criará `portfolio_images` (identidade, tenant, keys, metadados, dimensões, ordem, publicação, destaque, estado, autoria, timestamps e versão), `portfolio_processing_jobs` (jobs duráveis, tentativas, backoff, lease e erro sanitizado) e `tenant_portfolio_usage` (bytes reservados/usados, imagens, pendências e `portfolio_version`). Toda constraint será nomeada, FKs indexadas, `updated_at` mantido por trigger e queries tenant/status/ordem indexadas. A migração de tokens torna-se `V7__appointment_action_tokens.sql`.

Defaults configuráveis: 20 MiB/upload, 50 MP, lado de 12.000 px, 100 imagens não removidas, 2 GiB reservado/usado, três uploads pendentes, reserva por uma hora e concorrência um por worker. A linha de uso é bloqueada em transações curtas; Redis/proxy são defesa adicional, e uma flag operacional pode bloquear novos uploads sem impedir leitura/remoção.

## Storage e worker

`ObjectStoragePort` em `portfolio.application.port.out` expõe PUT pré-assinado, HEAD, download limitado, upload de derivada com MIME/tamanho explícitos, remoção idempotente e GET pré-assinado apenas de derivadas. `S3ObjectStorageAdapter` usa AWS SDK v2 exclusivamente na infraestrutura; domínio e aplicação não conhecem AWS ou Garage. O bucket, source e master são privados. Keys imutáveis: `portfolio/v1/tenants/{tenantId}/images/{imageId}/source/{sourceId}` e `.../revisions/{revision}/{master|display|thumbnail}.webp`.

Worker separado no mesmo artefato usa PostgreSQL, `FOR UPDATE SKIP LOCKED`, lease, até três tentativas e backoff. libvips roda fora do web process com timeout/limites: aceita JPEG/PNG/WebP estáticos, valida magic bytes, decodificação, dimensões/pixels, aplica orientação, sRGB e remove EXIF/XMP. Produz WebP sem upscale: master privado 4096 px, display 1920 px e thumbnail 480 px. Publica keys só quando todas as derivadas existem; então remove source bruto.

Reconciliadores tratam abandono, callback ausente, lease vencido, saída parcial, remoção incompleta, órfãos e drift. Remoção durante processamento vence e impede promoção a `AVAILABLE`; tombstones sem keys ficam 30 dias. Logs, auditoria e métricas não expõem URL assinada, filename, credenciais, conteúdo nem `tenantId` como label.
