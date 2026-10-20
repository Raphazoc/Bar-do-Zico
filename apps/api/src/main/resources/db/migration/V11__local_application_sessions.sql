SET search_path TO bar, pg_catalog;

-- Sessão opaca de duração fixa; apenas hash do segredo é persistido.
CREATE TABLE app_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    UNIQUE (establishment_id,id),
    FOREIGN KEY (establishment_id,user_id) REFERENCES users(establishment_id,id),
    CHECK (expires_at > created_at),
    CHECK (revoked_at >= created_at)
);
CREATE INDEX ix_app_sessions_user ON app_sessions(establishment_id,user_id);

-- Chaves SHA-256 de origem e identidade. Infraestrutura global sem dados de negócio.
CREATE TABLE login_attempts (
    key_hash varchar(64) PRIMARY KEY CHECK (length(key_hash) = 64),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    window_started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
