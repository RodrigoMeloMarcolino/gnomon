# ADR 0020 — Storage S3-compatible privado, upload direto e entrega pública mediada

Status: Accepted
Data: 2026-07-29

## Contexto

O portfólio tenant-owned precisa receber imagens sem transportar arquivos grandes pelo processo
web, mas não pode expor originais, masters sanitizados, credenciais ou URLs duráveis. Garage é o
primeiro provider previsto, sem acoplamento do domínio ou dos contratos à sua marca.

## Decisão

O módulo futuro `portfolio` terá um `ObjectStoragePort` em `application.port.out`, sem tipos de
AWS ou Garage. O adapter `S3ObjectStorageAdapter`, único lugar que conhece AWS SDK for Java v2,
usará endpoint customizado, região, path-style configurável, timeouts, pool e retries somente em
operações idempotentes. Erros do SDK serão convertidos em erros próprios de storage.

O bucket será privado e acessado por credencial dedicada. O backend cria PUTs pré-assinados de
curta duração para uploads simples e GETs pré-assinados somente para as derivadas `display` e
`thumbnail`, após autorizar cada leitura pública. Persistem-se object keys, nunca URLs completas:

```
portfolio/v1/tenants/{tenantId}/images/{imageId}/source/{sourceId}
portfolio/v1/tenants/{tenantId}/images/{imageId}/revisions/{revision}/master.webp
portfolio/v1/tenants/{tenantId}/images/{imageId}/revisions/{revision}/display.webp
portfolio/v1/tenants/{tenantId}/images/{imageId}/revisions/{revision}/thumbnail.webp
```

Não dependemos de ACL, policy, notificações nem versionamento do provider. O proxy do endpoint S3
impõe o limite físico do corpo: tamanho declarado em presigned PUT não é controle suficiente.

## Consequências

Uploads não passam pelo processo web e o provider pode ser trocado se mantiver as operações
centrais S3. O backend continua dono da autorização pública; master e original jamais são
entregues. Testes de contrato exercitarão o mesmo adapter contra Garage em container, incluindo
presigned PUT, HEAD, streaming, path-style, CORS, delete idempotente e indisponibilidade.

O MVP não inclui multipart, ACL pública, AWS real, nem dependência de recursos ausentes no Garage.
A compatibilidade usada como referência é a [documentação oficial do Garage](https://garagehq.deuxfleurs.fr/documentation/reference-manual/s3-compatibility/).
