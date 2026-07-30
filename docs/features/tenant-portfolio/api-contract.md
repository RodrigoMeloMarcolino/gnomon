# Contrato HTTP — Portfólio multi-tenant

Sucessos usam DTOs diretos `snake_case`; falhas usam `{"error":{"code","message","details"}}`. Administração exige owner/admin; cross-tenant retorna 403. Recursos públicos inexistentes, de outro tenant ou não publicados retornam 404.

| Método | Rota | Contrato |
| --- | --- | --- |
| POST | `/v1/tenants/{tenantSlug}/portfolio/images/uploads` | Exige `Idempotency-Key`; recebe filename, MIME e tamanho; cria reserva e PUT pré-assinado. |
| POST | `/v1/tenants/{tenantSlug}/portfolio/images/{imageId}/complete` | HEAD, valida e enfileira; 202 novo, 200 replay. |
| GET | `/v1/tenants/{tenantSlug}/portfolio/images` | Lista admin paginada, estado e `portfolio_version`. |
| GET/PATCH | `/v1/tenants/{tenantSlug}/portfolio/images/{imageId}` | Consulta ou altera alt, caption, publicação/destaque com versão otimista. |
| PUT | `/v1/tenants/{tenantSlug}/portfolio/images/order` | Ordem completa, máximo 100 IDs e `portfolio_version`. |
| POST | `/v1/tenants/{tenantSlug}/portfolio/images/{imageId}/retry` | Reagenda imagem `FAILED`. |
| DELETE | `/v1/tenants/{tenantSlug}/portfolio/images/{imageId}` | Oculta e agenda remoção; 202. |
| GET | `/v1/public/tenants/{tenantSlug}/portfolio` | Somente disponíveis/publicadas, paginação e ETag. |
| GET | `/v1/public/tenants/{tenantSlug}/portfolio/images/{imageId}/{variant}` | Apenas `display`/`thumbnail`; redireciona a GET pré-assinado curto. |

Upload retorna `image_id`, estado, método PUT, URL, headers e expiração de dez minutos. A mesma chave e payload retorna a imagem e URL renovada enquanto pendente; payload divergente retorna 409. URL expirada não impede `complete` se o objeto existir. Não existe entrega de master/source.
