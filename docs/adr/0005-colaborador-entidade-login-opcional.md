# ADR 0005 — Colaborador como entidade com login opcional

Status: Accepted
Data: 2026-07-23

## Contexto

No modelo multi-tenant, cada calendário pertence a um colaborador. É preciso decidir se
colaborador é sempre um usuário com conta, ou uma entidade gerenciada pelo tenant. Forçar conta
para todo colaborador cria fricção (dono precisaria convencer cada membro da equipe a se
cadastrar antes de montar a agenda); não ter vínculo nenhum impede um futuro painel do
colaborador.

## Decisão

1. `collaborators` é uma **entidade do tenant**: `id`, `tenant_id`, `user_id` (nullable, FK
   `users.id`), `display_name`, `is_active`, timestamps. Bio/foto são evoluções futuras.
2. **Login opcional**: `user_id` pode ser nulo — o colaborador existe apenas como entidade de
   agenda gerenciada por owner/admin. Quando vinculado a um usuário, esse vínculo representa a
   identidade de acesso do colaborador.
3. O vínculo usuário ↔ colaborador cria a base para a role `staff`: um usuário com membership
   `staff` no tenant gerencia apenas o calendário do colaborador ao qual está vinculado.
   `UNIQUE(tenant_id, user_id)` em `collaborators` garante no máximo um colaborador por usuário
   por tenant no MVP.
4. Cenário autônomo (1 tenant = 1 dono = 1 colaborador) é coberto naturalmente: o owner cria
   seu próprio colaborador, opcionalmente vinculado à própria conta.

## Consequências

- Onboarding não exige conta para membros da equipe: owner cadastra nomes e monta as agendas
  imediatamente.
- Autorização `staff` exige join `tenant_memberships` + `collaborators` (usuário → colaborador
  → calendário); regras detalhadas em `docs/specs/multi-tenancy.md`.
- Desvincular `user_id` não remove histórico de appointments; apenas remove o acesso staff.
- Se no futuro um usuário puder ser múltiplos colaboradores no mesmo tenant, basta remover a
  constraint de unicidade — decisão registrada para não bloquear.

## Rastreabilidade

- Decisão nova do Gnomon (sem equivalente no Moira, que tinha apenas 1 user → 1 provider).
- Relacionados: ADRs 0003, 0004, 0006.
