# ADR 0010 — Slots discretos de 15 minutos; não persistir slots disponíveis

Status: Accepted
Data: 2026-07-23

## Contexto

A unidade mínima de ocupação da agenda precisa ser simples e determinística para sustentar a
prevenção de conflitos. O Moira validou slots discretos de 15 minutos com disponibilidade
calculada dinamicamente.

## Decisão

1. **Unidade mínima = slot de 15 minutos**. Ao criar appointment, o sistema calcula os slot
   starts de `start_at` até antes de `end_at` (`end_at = start_at + duration_minutes_snapshot`).
2. `start_at` deve estar alinhado a boundary de 15 minutos; desalinhado é rejeitado.
3. **Não pré-gerar nem persistir slots disponíveis**: disponibilidade é calculada dinamicamente
   (regras semanais do calendário + duração do serviço + slots ocupados + instante atual).
   Apenas slots **ocupados** são persistidos, em `appointment_slots`.
4. Regras semanais são avaliadas na **timezone IANA do calendário**; respostas públicas e slots
   persistidos usam instantes UTC timezone-aware; comparações sempre via conversão explícita,
   nunca manipulando offset/tzinfo de forma implícita.
5. Regras semanais sobrepostas são permitidas e normalizadas (deduplicadas) no cálculo.
   Candidatos passados são filtrados contra o instante UTC atual (clock injetável).
6. Leitura de disponibilidade é **advisory**; a garantia final é a constraint do banco
   (ADR 0011).

## Consequências

- Serviços longos (ex.: tatuagem de 4h = 16 slots) funcionam naturalmente.
- Mudanças de disponibilidade não exigem migração de dados de agenda.
- O cálculo de disponibilidade é função de domínio pura e altamente testável (sem banco).
- Se o endpoint de disponibilidade ficar muito acessado, read model/cache é evolução possível
  (a fonte transacional continua sendo `appointment_slots`).

## Rastreabilidade

- Herda integralmente: Moira ADRs 0005 e 0006; livedoc seções 9, 10 e 12.
- Escopo da regra sobe do provider para o **calendário** (ADR 0006).
