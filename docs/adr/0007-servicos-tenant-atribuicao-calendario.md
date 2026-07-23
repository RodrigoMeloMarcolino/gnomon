# ADR 0007 — Serviços no tenant com atribuição por calendário

Status: Accepted
Data: 2026-07-23

## Contexto

No Moira, offerings pertenciam ao provider. Com múltiplos calendários por tenant, era preciso
decidir onde vive o catálogo: por calendário (cada colaborador cadastra os seus — duplica
serviços iguais), só no tenant (qualquer calendário atende qualquer serviço — irreal para
equipes com especialidades), ou tenant com atribuição.

## Decisão

1. **`offerings` pertence ao tenant**: `id`, `tenant_id`, `title`, `description`,
   `duration_minutes`, `price_cents`, `is_active`, timestamps.
2. **`calendar_offerings(calendar_id, offering_id)`** (PK composta) define quais calendários
   executam quais serviços. Atribuição é gestão de owner/admin.
3. **Regra de booking**: o serviço escolhido deve estar ativo **e** atribuído ao calendário
   escolhido; caso contrário o booking é rejeitado (erro de domínio) e o serviço não aparece na
   listagem filtrada por calendário.
4. Catálogo público do tenant (`GET /v1/public/tenants/{slug}/offerings`) lista os serviços
   ativos do tenant; a consulta de `available-slots` exige `calendar_id` + `offering_id`
   compatíveis com a atribuição.
5. Preço e duração continuam no offering; moeda herdada do tenant (ADR 0013).

## Consequências

- Negócios com serviços homogêneos atribuem tudo a todos; negócios com especialidades
  controlam finamente quem executa o quê.
- Remover uma atribuição não afeta appointments históricos (snapshot de duração — ADR 0012);
  apenas impede novos bookings daquela combinação.
- A unicidade de catálogo permanece por tenant:
  `UNIQUE(tenant_id, lower(title)) WHERE is_active = true`.
- Futuro preço/duração customizado por colaborador exigiria colunas de override em
  `calendar_offerings` — evolução aditiva, sem quebra.

## Rastreabilidade

- Decisão nova do Gnomon; estende o livedoc 6.3 (provider_offerings) para o modelo multi-tenant.
- Relacionados: ADRs 0003, 0006, 0012, 0013.
