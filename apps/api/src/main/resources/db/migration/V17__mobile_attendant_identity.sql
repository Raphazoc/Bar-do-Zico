SET search_path TO bar, pg_catalog;

-- Identidade interna para registrar o atendimento dos aparelhos sem conceder gestão.
-- Pessoas, senhas, perfis, contas e valores anteriores permanecem intactos.
ALTER TABLE users ADD COLUMN system_attendant boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT ck_users_system_attendant_identifier
    CHECK (NOT system_attendant OR (email IS NULL AND username IS NULL));
CREATE UNIQUE INDEX uq_users_system_attendant_unit ON users(establishment_id) WHERE system_attendant;
