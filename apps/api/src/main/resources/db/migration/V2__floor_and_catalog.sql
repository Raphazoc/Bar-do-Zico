SET search_path TO bar, pg_catalog;

CREATE TABLE areas (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(80) NOT NULL CHECK (btrim(name) <> ''),
    active boolean NOT NULL DEFAULT true,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, name)
);

CREATE TABLE bar_tables (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    area_id uuid NOT NULL,
    number varchar(20) NOT NULL CHECK (btrim(number) <> ''),
    status varchar(30) NOT NULL DEFAULT 'LIVRE'
        CHECK (status IN ('LIVRE','OCUPADA','RESERVADA','AGUARDANDO_PAGAMENTO','BLOQUEADA')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, number),
    FOREIGN KEY (establishment_id, area_id) REFERENCES areas(establishment_id, id)
);

CREATE TABLE table_authorizations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    user_id uuid NOT NULL,
    table_id uuid NOT NULL,
    granted_by uuid NOT NULL,
    granted_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at timestamptz CHECK (revoked_at >= granted_at),
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, user_id) REFERENCES users(establishment_id, id),
    FOREIGN KEY (establishment_id, table_id) REFERENCES bar_tables(establishment_id, id),
    FOREIGN KEY (establishment_id, granted_by) REFERENCES users(establishment_id, id)
);
CREATE UNIQUE INDEX uq_table_authorizations_active
    ON table_authorizations(establishment_id, user_id, table_id) WHERE revoked_at IS NULL;

CREATE TABLE categories (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    active boolean NOT NULL DEFAULT true,
    sort_order integer NOT NULL DEFAULT 0 CHECK (sort_order >= 0),
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, name)
);

CREATE TABLE products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    category_id uuid NOT NULL,
    name varchar(150) NOT NULL CHECK (btrim(name) <> ''),
    price numeric(14,2) NOT NULL CHECK (price >= 0),
    production_destination varchar(20) NOT NULL
        CHECK (production_destination IN ('COZINHA','BAR','SEM_PREPARO')),
    available boolean NOT NULL DEFAULT true,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, category_id) REFERENCES categories(establishment_id, id)
);

CREATE TABLE modifier_groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    min_choices integer NOT NULL DEFAULT 0 CHECK (min_choices >= 0),
    max_choices integer NOT NULL CHECK (max_choices >= min_choices AND max_choices > 0),
    UNIQUE (establishment_id, id)
);

CREATE TABLE modifiers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    group_id uuid NOT NULL,
    name varchar(100) NOT NULL CHECK (btrim(name) <> ''),
    price numeric(14,2) NOT NULL CHECK (price >= 0),
    active boolean NOT NULL DEFAULT true,
    UNIQUE (establishment_id, id),
    FOREIGN KEY (establishment_id, group_id) REFERENCES modifier_groups(establishment_id, id)
);

CREATE TABLE product_modifier_groups (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    establishment_id uuid NOT NULL REFERENCES establishments(id),
    product_id uuid NOT NULL,
    group_id uuid NOT NULL,
    UNIQUE (establishment_id, id),
    UNIQUE (establishment_id, product_id, group_id),
    FOREIGN KEY (establishment_id, product_id) REFERENCES products(establishment_id, id),
    FOREIGN KEY (establishment_id, group_id) REFERENCES modifier_groups(establishment_id, id)
);
