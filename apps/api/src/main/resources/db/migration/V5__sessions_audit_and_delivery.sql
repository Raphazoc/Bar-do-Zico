SET search_path TO bar, pg_catalog;

CREATE TABLE devices (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    label varchar(120) NOT NULL CHECK (btrim(label) <> ''),
    blocked_at timestamptz,
    blocked_by uuid,
    app_version varchar(40) NOT NULL,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, blocked_by) REFERENCES users(establishment_id, id),
    CHECK ((blocked_at IS NULL) = (blocked_by IS NULL))
);

CREATE TABLE device_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    device_id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, device_id) REFERENCES devices(establishment_id, id),
    FOREIGN KEY (establishment_id, user_id) REFERENCES users(establishment_id, id),
    CHECK (expires_at > created_at),
    CHECK (last_seen_at >= created_at),
    CHECK (revoked_at >= created_at)
);

CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    session_id uuid NOT NULL,
    token_hash varchar(255) NOT NULL UNIQUE CHECK (length(token_hash) >= 32),
    family_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, session_id) REFERENCES device_sessions(establishment_id, id),
    CHECK (expires_at > created_at),
    CHECK (used_at >= created_at),
    CHECK (revoked_at >= created_at)
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    actor_id uuid,
    action varchar(100) NOT NULL CHECK (btrim(action) <> ''),
    resource_type varchar(80) NOT NULL CHECK (btrim(resource_type) <> ''),
    resource_id uuid NOT NULL,
    reason text,
    changes jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(changes) = 'object'),
    correlation_id varchar(100) NOT NULL CHECK (btrim(correlation_id) <> ''),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, actor_id) REFERENCES users(establishment_id, id)
);

CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    user_id uuid NOT NULL,
    operation varchar(80) NOT NULL CHECK (btrim(operation) <> ''),
    key varchar(150) NOT NULL CHECK (btrim(key) <> ''),
    request_hash varchar(128) NOT NULL CHECK (length(request_hash) >= 32),
    status varchar(20) NOT NULL DEFAULT 'PROCESSANDO' CHECK (status IN ('PROCESSANDO','CONCLUIDO')),
    response_code integer CHECK (response_code BETWEEN 100 AND 599),
    response_body jsonb,
    resource_type varchar(80),
    resource_id uuid,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamptz,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, user_id, operation, key),
    FOREIGN KEY (establishment_id, user_id) REFERENCES users(establishment_id, id),
    CHECK ((resource_type IS NULL) = (resource_id IS NULL)),
    CHECK (expires_at > created_at),
    CHECK ((status = 'CONCLUIDO' AND response_code IS NOT NULL AND response_body IS NOT NULL)
        OR (status = 'PROCESSANDO' AND response_code IS NULL AND response_body IS NULL)),
    CHECK (resource_type NOT IN ('payment','payment_reversal') OR expires_at IS NULL)
);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    aggregate_type varchar(80) NOT NULL CHECK (btrim(aggregate_type) <> ''),
    aggregate_id uuid NOT NULL,
    event_type varchar(100) NOT NULL CHECK (btrim(event_type) <> ''),
    payload jsonb NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz,
    UNIQUE (establishment_id, id),
    CHECK (published_at >= occurred_at)
);
