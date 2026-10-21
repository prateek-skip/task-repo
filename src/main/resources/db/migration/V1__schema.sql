CREATE TABLE categories (
    id          uuid         PRIMARY KEY,
    slug        varchar(64)  NOT NULL UNIQUE,
    name        varchar(128) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id           uuid          PRIMARY KEY,
    sku          varchar(64)   NOT NULL,
    name         varchar(255)  NOT NULL,
    description  text,
    category_id  uuid          NOT NULL REFERENCES categories (id),
    -- Exact decimal for money; never a float.
    price        numeric(12,2) NOT NULL CHECK (price >= 0),
    currency     varchar(3)    NOT NULL DEFAULT 'USD',
    -- ACTIVE | INACTIVE | ARCHIVED. Delete is a status change so orders and
    -- analytics keep a valid reference.
    status       varchar(16)   NOT NULL,
    -- Free-form per-product attributes (colour, material, ...). Keeps the
    -- relational core strict while allowing document-style variety.
    attributes   jsonb,
    -- Optimistic locking; also doubles as the HTTP ETag.
    version      bigint        NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    -- Named so the application can recognise a duplicate SKU without parsing driver messages.
    CONSTRAINT uq_products_sku UNIQUE (sku)
);
