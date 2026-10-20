SET search_path TO bar, pg_catalog;

CREATE TABLE accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    table_id uuid,
    opened_by uuid NOT NULL,
    status varchar(25) NOT NULL DEFAULT 'ABERTA'
        CHECK (status IN ('ABERTA','EM_FECHAMENTO','FECHADA','CANCELADA')),
    opened_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, table_id) REFERENCES bar_tables(establishment_id, id),
    FOREIGN KEY (establishment_id, opened_by) REFERENCES users(establishment_id, id),
    CHECK ((status IN ('FECHADA','CANCELADA')) = (closed_at IS NOT NULL)),
    CHECK (closed_at >= opened_at)
);
CREATE UNIQUE INDEX uq_accounts_active_table ON accounts(establishment_id, table_id)
    WHERE table_id IS NOT NULL AND status IN ('ABERTA','EM_FECHAMENTO');

CREATE TABLE orders (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    account_id uuid NOT NULL,
    waiter_id uuid NOT NULL,
    -- Numeração global monotônica; não promete sequência sem lacunas por unidade.
    number bigint GENERATED ALWAYS AS IDENTITY,
    sent_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes text,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, number),
    FOREIGN KEY (establishment_id, account_id) REFERENCES accounts(establishment_id, id),
    FOREIGN KEY (establishment_id, waiter_id) REFERENCES users(establishment_id, id)
);

CREATE TABLE order_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    order_id uuid NOT NULL,
    product_id uuid NOT NULL,
    description_snapshot text NOT NULL CHECK (btrim(description_snapshot) <> ''),
    unit_price numeric(14,2) NOT NULL CHECK (unit_price >= 0),
    quantity numeric(14,3) NOT NULL CHECK (quantity > 0),
    destination varchar(20) NOT NULL CHECK (destination IN ('COZINHA','BAR','SEM_PREPARO')),
    status varchar(25) NOT NULL DEFAULT 'ENVIADO'
        CHECK (status IN ('ENVIADO','ACEITO','EM_PREPARO','PRONTO','ENTREGUE','CANCELADO')),
    tax_rules_snapshot jsonb NOT NULL DEFAULT '{}' CHECK (jsonb_typeof(tax_rules_snapshot) = 'object'),
    notes text,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, order_id) REFERENCES orders(establishment_id, id),
    FOREIGN KEY (establishment_id, product_id) REFERENCES products(establishment_id, id)
);

CREATE TABLE order_item_modifiers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    order_item_id uuid NOT NULL,
    modifier_id uuid NOT NULL,
    description_snapshot text NOT NULL CHECK (btrim(description_snapshot) <> ''),
    unit_price numeric(14,2) NOT NULL CHECK (unit_price >= 0),
    -- Quantidade do complemento por unidade do item pai.
    quantity numeric(14,3) NOT NULL DEFAULT 1 CHECK (quantity > 0),
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, order_item_id, modifier_id),
    FOREIGN KEY (establishment_id, order_item_id) REFERENCES order_items(establishment_id, id),
    FOREIGN KEY (establishment_id, modifier_id) REFERENCES modifiers(establishment_id, id)
);

CREATE TABLE production_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    order_item_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    authorized_by uuid,
    from_status varchar(25) NOT NULL CHECK (from_status IN
        ('ENVIADO','ACEITO','EM_PREPARO','PRONTO','ENTREGUE','CANCELADO')),
    to_status varchar(25) NOT NULL CHECK (to_status IN
        ('ENVIADO','ACEITO','EM_PREPARO','PRONTO','ENTREGUE','CANCELADO')),
    reason text,
    stock_disposition varchar(30) CHECK (stock_disposition IN ('SEM_CONSUMO','PERDA','RETORNO_FISICO')),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, order_item_id) REFERENCES order_items(establishment_id, id),
    FOREIGN KEY (establishment_id, actor_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, authorized_by) REFERENCES users(establishment_id, id),
    CHECK (from_status <> to_status),
    CHECK (to_status <> 'CANCELADO' OR (reason IS NOT NULL AND btrim(reason) <> '')),
    CHECK (to_status <> 'CANCELADO' OR from_status NOT IN ('EM_PREPARO','PRONTO','ENTREGUE')
        OR (stock_disposition IS NOT NULL AND stock_disposition IN ('PERDA','RETORNO_FISICO')))
);

CREATE TABLE account_adjustments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    account_id uuid NOT NULL,
    order_item_id uuid,
    actor_id uuid NOT NULL,
    authorized_by uuid,
    reverses_id uuid,
    kind varchar(25) NOT NULL CHECK (kind IN ('DESCONTO','SERVICO','ACRESCIMO')),
    amount numeric(14,2) NOT NULL CHECK (amount > 0),
    rate numeric(7,4) CHECK (rate BETWEEN 0 AND 100),
    base_amount numeric(14,2) CHECK (base_amount >= 0),
    reason text NOT NULL CHECK (btrim(reason) <> ''),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, reverses_id),
    FOREIGN KEY (establishment_id, account_id) REFERENCES accounts(establishment_id, id),
    FOREIGN KEY (establishment_id, order_item_id) REFERENCES order_items(establishment_id, id),
    FOREIGN KEY (establishment_id, actor_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, authorized_by) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, reverses_id) REFERENCES account_adjustments(establishment_id, id),
    CHECK (reverses_id IS NULL OR reverses_id <> id),
    CHECK (kind <> 'SERVICO' OR (rate IS NOT NULL AND base_amount IS NOT NULL
        AND amount = round(base_amount * rate / 100, 2)))
);
