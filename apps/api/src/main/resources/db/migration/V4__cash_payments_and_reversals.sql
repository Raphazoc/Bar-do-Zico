SET search_path TO bar, pg_catalog;

CREATE TABLE cash_terminals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    active boolean NOT NULL DEFAULT true,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, name)
);

CREATE TABLE cash_sessions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    terminal_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    opening_amount numeric(14,2) NOT NULL CHECK (opening_amount >= 0),
    opened_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at timestamptz,
    status varchar(20) NOT NULL DEFAULT 'ABERTA' CHECK (status IN ('ABERTA','FECHADA')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, terminal_id) REFERENCES cash_terminals(establishment_id, id),
    FOREIGN KEY (establishment_id, operator_id) REFERENCES users(establishment_id, id),
    CHECK ((status = 'FECHADA') = (closed_at IS NOT NULL)),
    CHECK (closed_at >= opened_at)
);
CREATE UNIQUE INDEX uq_cash_sessions_open_terminal ON cash_sessions(establishment_id, terminal_id)
    WHERE status = 'ABERTA';

CREATE TABLE payments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    account_id uuid NOT NULL,
    cash_session_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    method varchar(15) NOT NULL CHECK (method IN ('DINHEIRO','PIX','DEBITO','CREDITO','VOUCHER','OUTRO')),
    tendered_amount numeric(14,2) NOT NULL CHECK (tendered_amount > 0),
    applied_amount numeric(14,2) NOT NULL CHECK (applied_amount > 0),
    change_amount numeric(14,2) NOT NULL DEFAULT 0 CHECK (change_amount >= 0),
    received_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, account_id) REFERENCES accounts(establishment_id, id),
    FOREIGN KEY (establishment_id, cash_session_id) REFERENCES cash_sessions(establishment_id, id),
    FOREIGN KEY (establishment_id, operator_id) REFERENCES users(establishment_id, id),
    CHECK (tendered_amount = applied_amount + change_amount),
    CHECK (method = 'DINHEIRO' OR change_amount = 0)
);

CREATE TABLE payment_reversals (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    payment_id uuid NOT NULL,
    cash_session_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    authorized_by uuid NOT NULL,
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    refund_method varchar(15) NOT NULL CHECK (refund_method IN
        ('DINHEIRO','PIX','DEBITO','CREDITO','VOUCHER','OUTRO')),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    reversed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, payment_id) REFERENCES payments(establishment_id, id),
    FOREIGN KEY (establishment_id, cash_session_id) REFERENCES cash_sessions(establishment_id, id),
    FOREIGN KEY (establishment_id, operator_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, authorized_by) REFERENCES users(establishment_id, id)
);

CREATE TABLE cash_movements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    cash_session_id uuid NOT NULL,
    operator_id uuid NOT NULL,
    authorized_by uuid,
    kind varchar(20) NOT NULL CHECK (kind IN ('SUPRIMENTO','SANGRIA')),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, cash_session_id) REFERENCES cash_sessions(establishment_id, id),
    FOREIGN KEY (establishment_id, operator_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, authorized_by) REFERENCES users(establishment_id, id)
);

CREATE TABLE cash_closures (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    cash_session_id uuid NOT NULL,
    closed_by uuid NOT NULL,
    corrects_id uuid,
    reason text,
    closed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, corrects_id),
    FOREIGN KEY (establishment_id, cash_session_id) REFERENCES cash_sessions(establishment_id, id),
    FOREIGN KEY (establishment_id, closed_by) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, corrects_id) REFERENCES cash_closures(establishment_id, id),
    CHECK (corrects_id IS NULL OR (corrects_id <> id AND reason IS NOT NULL AND btrim(reason) <> ''))
);
CREATE UNIQUE INDEX uq_cash_closures_original ON cash_closures(establishment_id, cash_session_id)
    WHERE corrects_id IS NULL;

CREATE TABLE cash_closure_lines (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    closure_id uuid NOT NULL,
    method varchar(15) NOT NULL CHECK (method IN ('DINHEIRO','PIX','DEBITO','CREDITO','VOUCHER','OUTRO')),
    expected_amount numeric(14,2) NOT NULL,
    counted_amount numeric(14,2) NOT NULL CHECK (counted_amount >= 0),
    difference_amount numeric(14,2) GENERATED ALWAYS AS (counted_amount - expected_amount) STORED,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, closure_id, method),
    FOREIGN KEY (establishment_id, closure_id) REFERENCES cash_closures(establishment_id, id)
);
