-- Executar com psql como proprietário após as migrações.
-- A senha é lida do ambiente BAR_APP_PASSWORD, não de argumentos do processo.
\set ON_ERROR_STOP on
\getenv app_password BAR_APP_PASSWORD
\if :{?app_password}
SELECT format('CREATE ROLE bar_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bar_app')
\gexec
GRANT CONNECT ON DATABASE :"DBNAME" TO bar_app;
GRANT USAGE ON SCHEMA bar TO bar_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bar TO bar_app;
REVOKE ALL ON bar.flyway_schema_history FROM bar_app;
REVOKE INSERT, UPDATE, DELETE ON bar.permissions FROM bar_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA bar TO bar_app;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA bar FROM PUBLIC;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA bar TO bar_app;
\else
\echo 'Defina BAR_APP_PASSWORD antes de executar este arquivo.'
\quit 1
\endif
