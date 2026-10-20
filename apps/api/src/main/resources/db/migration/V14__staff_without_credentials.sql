SET search_path TO bar, pg_catalog;
-- Funcionários podem ser apenas cadastros operacionais. Acessos antigos permanecem.
ALTER TABLE users DROP CONSTRAINT ck_users_login_identifier;
