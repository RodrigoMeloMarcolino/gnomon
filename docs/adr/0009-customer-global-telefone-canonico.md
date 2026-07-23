# ADR 0009 — Customer global por telefone canônico

Status: Accepted
Data: 2026-07-23

## Contexto

Clientes finais precisam de histórico e reaproveitamento sem virarem usuários da plataforma. O
Moira decidiu (ADRs 0003/0004): customer separado de user, reutilizado **globalmente** pelo
telefone canônico. Com multi-tenancy, a alternativa seria customer por tenant
(`UNIQUE(tenant_id, phone)`), que duplicaria o mesmo cliente entre tenants e quebraria a
decisão histórica e o blueprint do checkpoint 37.12.

## Decisão

1. **Customer ≠ User** (mantido): customers não têm senha, sessão, JWT, roles ou ownership.
   Existem para relacionamento comercial: histórico, reaproveitamento, CRM futuro, lembretes.
2. **Reuso global por telefone canônico**: `customers.phone` único global, já persistido em
   formato canônico (E.164). Não existe `canonical_phone` separado. E-mail é opcional e não é
   chave de identidade.
3. **Sem `tenant_id` em `customers`**: a relação customer ↔ tenant é inferida pelos
   appointments. Dados de CRM específicos do tenant (notas internas, tags, preferências) ficam
   fora do customer global — tabela futura `tenant_customers`; no MVP, notas pontuais vivem em
   `appointments.customer_notes`.
4. Validação e canonização do telefone acontecem **antes** de buscar/criar customer. No MVP,
   nome/e-mail submetidos para telefone já existente são descartados (atualização automática de
   perfil global permanece adiada, como no Moira).
5. Confirmação de telefone via SMS/WhatsApp é evolução futura (`phone_verified_at`).

## Consequências

- Um mesmo cliente pode agendar em tenants diferentes com o mesmo telefone mantendo identidade
  única de customer.
- Corrida na criação de customer (mesmo telefone, requests simultâneas) resolve-se com retry no
  conflito de `UNIQUE(phone)` dentro da transação de booking — padrão já validado no Moira
  (task 02).
- Privacidade: dados globais do customer são mínimos (nome, telefone, e-mail); qualquer dado
  sensível do relacionamento é tenant-scoped.

## Rastreabilidade

- Herda integralmente: Moira ADRs 0003 e 0004; livedoc seções 6.5 e 8; blueprint 37.12
  (exceção de modelagem preservada).
