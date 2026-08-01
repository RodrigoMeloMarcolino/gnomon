-- Deterministic development fixture. It is intentionally outside Flyway migrations.
BEGIN;

DELETE FROM appointment_slots WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM appointments WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM availability_rules WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM calendar_offerings WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM calendars WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM collaborators WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM offerings WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM tenant_memberships WHERE tenant_id = (SELECT id FROM tenants WHERE slug = 'umbra-smoke');
DELETE FROM tenants WHERE slug = 'umbra-smoke';

INSERT INTO users (id, keycloak_sub, email, display_name)
VALUES ('10000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'dev@gnomon.local', 'Dev Gnomon')
ON CONFLICT (keycloak_sub) DO UPDATE SET email = EXCLUDED.email, display_name = EXCLUDED.display_name;

INSERT INTO tenants (id, name, slug, timezone, currency_code)
VALUES ('20000000-0000-4000-8000-000000000001', 'Umbra Smoke', 'umbra-smoke', 'America/Fortaleza', 'BRL');
INSERT INTO collaborators (id, tenant_id, display_name)
VALUES ('21000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Lia Martins');
INSERT INTO calendars (id, tenant_id, collaborator_id, name, timezone)
VALUES ('30000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000001', 'Agenda da Lia', 'America/Fortaleza');
INSERT INTO offerings (id, tenant_id, title, duration_minutes, price_cents)
VALUES ('40000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', 'Corte Solar', 30, 8000);
INSERT INTO calendar_offerings (tenant_id, calendar_id, offering_id)
VALUES ('20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001');
INSERT INTO availability_rules (tenant_id, calendar_id, weekday, start_time, end_time)
SELECT '20000000-0000-4000-8000-000000000001', '30000000-0000-4000-8000-000000000001', weekday, TIME '09:00', TIME '12:00'
FROM generate_series(1, 7) AS weekday;
INSERT INTO tenant_memberships (tenant_id, user_id, role)
VALUES ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'owner');

COMMIT;
