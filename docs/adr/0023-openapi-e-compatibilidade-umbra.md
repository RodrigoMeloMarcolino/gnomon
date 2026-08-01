# ADR 0023 — OpenAPI e compatibilidade de integração Umbra

Status: Accepted  
Data: 2026-08-01

## Decisão

O contrato de integração é gerado pelo código com Springdoc e publicado em `/v3/api-docs` e
`/v3/api-docs.yaml`. O Swagger UI fica disponível em `/swagger-ui.html` somente nos profiles
`local` e `docker`; em produção apenas os documentos JSON/YAML continuam públicos.

Os endpoints consumidos por Umbra usam `snake_case`. DTOs públicos específicos preservam os
contratos administrativos em `camelCase`. Slots e booking já usam `snake_case` e permanecem
inalterados. Compatibilidade futura exige endpoint/DTO novo ou versão explícita: não se renomeia
campo já publicado.

O `Idempotency-Key` de booking é UUID canônico minúsculo. Ausência ou formato inválido retorna
`422 validation_error`; replay normalizado retorna `200`; payload divergente retorna `409
idempotency_key_conflict`. A rejeição advisory de slot continua `422 slot_unavailable` e a
constraint concorrente continua `409 slot_unavailable`.

## Consequências

Umbra substitui schemas temporários pelo OpenAPI publicado. OpenAPI é documentação derivada do
código, não geração de controllers/DTOs nesta etapa. CORS continua sem `PUT` e callbacks OIDC
locais são explícitos.
