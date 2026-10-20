-- PostgreSQL 17. Todas as entidades operacionais pertencem a uma unidade.
CREATE SCHEMA IF NOT EXISTS bar;
SET search_path TO bar, pg_catalog;

CREATE TABLE establishments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(150) NOT NULL CHECK (btrim(name) <> ''),
    timezone varchar(60) NOT NULL DEFAULT 'America/Sao_Paulo',
    service_rate numeric(7,4) NOT NULL DEFAULT 0 CHECK (service_rate BETWEEN 0 AND 100),
    service_enabled boolean NOT NULL DEFAULT false,
    settings jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(settings) = 'object'),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(150) NOT NULL CHECK (btrim(name) <> ''),
    email varchar(254) NOT NULL CHECK (email = lower(btrim(email)) AND position('@' IN email) > 1),
    password_hash text NOT NULL CHECK (length(password_hash) >= 20),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, email)
);

CREATE TABLE roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(80) NOT NULL CHECK (btrim(name) <> ''),
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, name)
);

CREATE TABLE permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE CHECK (btrim(code) <> ''),
    description text NOT NULL
);

CREATE TABLE user_roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, user_id, role_id),
    FOREIGN KEY (establishment_id, user_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, role_id) REFERENCES roles(establishment_id, id)
);

CREATE TABLE role_permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL REFERENCES permissions(id),
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, role_id, permission_id),
    FOREIGN KEY (establishment_id, role_id) REFERENCES roles(establishment_id, id)
);
