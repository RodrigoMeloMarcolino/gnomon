# Roadmap inicial — Gnomon Node.js

| Fase | Entrega                                                               | Status |
| ---- | --------------------------------------------------------------------- | ------ |
| 00   | [Fundação documental, contrato e spike ORM](00-foundation-typeorm.md) | doing  |
| 01   | Runtime, config, erros, health, logs e CI                             | done   |
| 02   | Keycloak, users, tenants e memberships                                | todo   |
| 03   | Collaborators, calendars e offerings                                  | todo   |
| 04   | Availability e equivalência temporal                                  | todo   |
| 05   | Booking transacional com GiST e idempotência                          | todo   |
| 06   | Painel administrativo e customers                                     | todo   |
| 07   | Redis, observabilidade e hardening                                    | todo   |
| 08   | Contract tests, shadow read, canário e cutover                        | todo   |

O booking deve ganhar um spike técnico ainda na fase 00, antes do CRUD completo, porque ORM,
tradução de constraints e concorrência são os maiores riscos da reimplementação.

A fundação e o spike ORM estão concluídos. A fase 00 permanece `doing` exclusivamente até a
materialização do OpenAPI e dos cenários do `gnomon-mock` descritos no baseline de compatibilidade.
