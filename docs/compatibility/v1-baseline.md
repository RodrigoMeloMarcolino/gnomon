# Baseline de compatibilidade da API `/v1`

## Autoridades durante a transição

- `gnomon-mock`: referência executável e origem do primeiro snapshot OpenAPI.
- `gnomon`: sucessor de produção e futuro proprietário do contrato.
- `umbra`: consumidor; não redefine regras do backend.

## Artefatos a materializar

- OpenAPI JSON/YAML congelado e versionado;
- fixtures do tenant `umbra-smoke` sem PII real;
- matriz endpoint → status → código de erro;
- cenários black-box de catálogo, disponibilidade, booking, auth e administração;
- relatório de OpenAPI diff por pull request.

## Estado local em 2026-08-19

A cópia disponível de `gnomon-mock` contém somente a mesma fundação documental e não possui
runtime/OpenAPI extraível. O comando `pnpm contracts:diff` está preparado para comparar
`openapi-v1-baseline.json` com `openapi-v1-current.json`, mas não integra o CI enquanto esses
artefatos reais não forem fornecidos. Nenhum contrato fictício será usado para liberar cutover.

## Regra de equivalência

As duas APIs são compatíveis quando o mesmo request válido ou inválido, no mesmo estado lógico,
produz status, headers relevantes e payload semanticamente equivalentes. Ordem de propriedades
JSON, IDs gerados e detalhes internos de persistência não participam da equivalência, salvo quando
o contrato exige estabilidade, como replay de idempotência.

## Gate de cutover

Nenhum ambiente do Umbra aponta para o sucessor até a suíte comum, segurança real, concorrência
PostgreSQL, OpenAPI diff e rollback por configuração estarem verdes.
