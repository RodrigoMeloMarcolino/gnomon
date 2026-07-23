# ADR 0008 — Guest booking sem conta obrigatória

Status: Accepted
Data: 2026-07-23

## Contexto

Exigir cadastro do cliente final é barreira de adoção: o prestador quer divulgar um link e
receber agendamentos, não explicar a cada cliente que é preciso criar conta. O Keycloak entra na
stack apenas para os usuários da plataforma; nada muda para o cliente final.

## Decisão

1. Clientes finais **não criam conta** para agendar. O fluxo público pede apenas nome,
   telefone/WhatsApp, e-mail opcional e observações opcionais.
2. Endpoints públicos de booking (`/v1/public/**`) são **sem autenticação**. O Keycloak não
   emite tokens para customers.
3. Autenticação obrigatória apenas para usuários da plataforma: owners, admins e staff.
4. Ações futuras do cliente final sobre um appointment (cancelar, remarcar) serão autorizadas
   por **tokens seguros por appointment** (armazenados como hash), nunca por identidade
   autenticada de customer.

## Consequências

- `SecurityFilterChain` configura `/v1/public/**` como `permitAll` e o restante exige JWT.
- Endpoints públicos devem ser projetados contra abuso (rate limiting futuro, validação forte
  de entrada, idempotência — ADR 0014).
- O funil de conversão permanece: link → serviço → horário → nome + telefone → agendado.

## Rastreabilidade

- Herda integralmente: Moira ADR 0002; livedoc seção 7 (decisão de produto: guest booking).
