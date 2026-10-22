SET search_path TO bar, pg_catalog;

-- Os nomes declarados nos celulares identificam lancamentos, sem conceder gestao.
ALTER TABLE users ADD COLUMN named_attendant boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT ck_named_attendant_system CHECK (NOT named_attendant OR system_attendant);
DROP INDEX uq_users_system_attendant_unit;
CREATE UNIQUE INDEX uq_users_system_attendant_unit ON users(establishment_id)
 WHERE system_attendant AND NOT named_attendant;
CREATE UNIQUE INDEX uq_users_named_attendant_name ON users(establishment_id,lower(name))
 WHERE system_attendant AND named_attendant;
