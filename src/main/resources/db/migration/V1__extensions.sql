-- Extensões base do schema Gnomon (PRD §10).
-- gen_random_uuid() (pgcrypto) e e-mails case-insensitive (citext) nas fases seguintes.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;
