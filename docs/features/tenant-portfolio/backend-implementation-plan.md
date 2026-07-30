# Plano de implementação futura — Portfólio multi-tenant

1. Congelar contratos, propriedades, códigos de erro e DDL V6.
2. Implementar domínio, migration, contadores bloqueáveis e quotas.
3. Implementar `ObjectStoragePort` e adapter S3-compatible.
4. Criar/renovar upload idempotente.
5. Confirmar upload e enfileirar job na mesma transação.
6. Implementar worker, sanitização e derivadas.
7. Expor APIs administrativas de leitura/estado.
8. Implementar publicação, destaque e reordenação otimista.
9. Expor catálogo público, ETag e redirect assinado.
10. Implementar remoção e reconciliadores.
11. Adicionar observabilidade, auditoria e hardening.
12. Adicionar Compose/Garage, backup e documentação operacional.

Cada entrega deve ser mergeável isoladamente. Testes obrigatórios incluem unidade (estado, keys, quotas, idempotência, versão, retries e compensação), HTTP (headers, JSON, paginação, ETag, redirects e erros), PostgreSQL (constraints, quotas, order e corridas job×remoção) e Garage em `GenericContainer` (presign, HEAD, streaming, path-style, CORS e delete). Não usar AWS real; fixtures de imagem serão pequenas e adversariais controladas.
