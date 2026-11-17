-- ==========================================================
-- Fatos de pedidos e itens (desnormalizados a partir do order-api)
-- ==========================================================
CREATE TABLE analytics_order (
    order_id          BIGINT PRIMARY KEY,
    customer_id       BIGINT,
    status            VARCHAR(32)    NOT NULL,
    currency          VARCHAR(3),
    order_date        TIMESTAMPTZ,
    source_updated_at TIMESTAMPTZ    NOT NULL,
    confirmed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    subtotal          NUMERIC(19, 2) NOT NULL DEFAULT 0,
    discount_amount   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    shipping_amount   NUMERIC(19, 2) NOT NULL DEFAULT 0,
    total_amount      NUMERIC(19, 2) NOT NULL DEFAULT 0,
    zip_code          VARCHAR(9),
    city              VARCHAR(120),
    state             VARCHAR(2),
    region            VARCHAR(16),
    -- Data da venda no fuso America/Sao_Paulo (confirmedAt; orderDate na ausencia).
    sale_date         DATE,
    -- Pedido CONFIRMED e em BRL: entra nos indicadores de vendas.
    counts_as_sale    BOOLEAN        NOT NULL DEFAULT FALSE,
    collected_at      TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_analytics_order_sale ON analytics_order (counts_as_sale, sale_date);
CREATE INDEX idx_analytics_order_state ON analytics_order (state);

CREATE TABLE analytics_order_item (
    id             BIGSERIAL PRIMARY KEY,
    order_id       BIGINT         NOT NULL REFERENCES analytics_order (order_id) ON DELETE CASCADE,
    source_item_id BIGINT,
    product_id     BIGINT         NOT NULL,
    product_name   TEXT,
    brand          TEXT,
    category_id    BIGINT,
    category_name  TEXT,
    quantity       INTEGER        NOT NULL,
    unit_price     NUMERIC(19, 2) NOT NULL,
    item_total     NUMERIC(19, 2) NOT NULL,
    price_range    VARCHAR(32)    NOT NULL
);

CREATE INDEX idx_analytics_item_order ON analytics_order_item (order_id);
CREATE INDEX idx_analytics_item_product ON analytics_order_item (product_id);
CREATE INDEX idx_analytics_item_category ON analytics_order_item (category_id);

-- ==========================================================
-- Agregacoes pre-calculadas (recalculadas por data afetada)
-- ==========================================================
CREATE TABLE agg_sales_daily (
    sale_date   DATE           NOT NULL,
    state       VARCHAR(2)     NOT NULL,
    region      VARCHAR(16)    NOT NULL,
    order_count BIGINT         NOT NULL,
    units_sold  BIGINT         NOT NULL,
    revenue     NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (sale_date, state)
);

CREATE TABLE agg_product_sales_daily (
    sale_date     DATE           NOT NULL,
    product_id    BIGINT         NOT NULL,
    state         VARCHAR(2)     NOT NULL,
    region        VARCHAR(16)    NOT NULL,
    product_name  TEXT,
    brand         TEXT,
    category_id   BIGINT,
    category_name TEXT,
    order_count   BIGINT         NOT NULL,
    units_sold    BIGINT         NOT NULL,
    revenue       NUMERIC(19, 2) NOT NULL,
    PRIMARY KEY (sale_date, product_id, state)
);

CREATE INDEX idx_agg_product_category ON agg_product_sales_daily (category_id, sale_date);

-- ==========================================================
-- Estoque
-- ==========================================================
CREATE TABLE inventory_snapshot (
    id                 BIGSERIAL PRIMARY KEY,
    captured_at        TIMESTAMPTZ NOT NULL,
    inventory_count    BIGINT      NOT NULL,
    quantity_on_hand   BIGINT      NOT NULL,
    quantity_reserved  BIGINT      NOT NULL,
    available_quantity BIGINT      NOT NULL,
    out_of_stock_count BIGINT      NOT NULL,
    low_stock_count    BIGINT      NOT NULL
);

CREATE INDEX idx_inventory_snapshot_captured ON inventory_snapshot (captured_at);

CREATE TABLE inventory_product_status (
    product_id          BIGINT PRIMARY KEY,
    product_type        VARCHAR(32),
    quantity_on_hand    BIGINT      NOT NULL,
    quantity_reserved   BIGINT      NOT NULL,
    available_quantity  BIGINT      NOT NULL,
    low_stock_threshold BIGINT      NOT NULL,
    low_stock           BOOLEAN     NOT NULL,
    out_of_stock        BOOLEAN     NOT NULL,
    source_updated_at   TIMESTAMPTZ,
    collected_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE inventory_movement (
    movement_id    BIGINT PRIMARY KEY,
    product_id     BIGINT      NOT NULL,
    movement_type  VARCHAR(32) NOT NULL,
    quantity_delta BIGINT      NOT NULL,
    reason         TEXT,
    order_id       BIGINT,
    reservation_id BIGINT,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_inventory_movement_created ON inventory_movement (created_at);
CREATE INDEX idx_inventory_movement_product ON inventory_movement (product_id, created_at);

-- ==========================================================
-- Controle de coleta
-- ==========================================================
CREATE TABLE collection_checkpoint (
    source          VARCHAR(64) PRIMARY KEY,
    cursor_at       TIMESTAMPTZ,
    cursor_page     INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_status     VARCHAR(16),
    last_error      TEXT
);

CREATE TABLE collection_run (
    id                BIGSERIAL PRIMARY KEY,
    source            VARCHAR(64) NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ,
    status            VARCHAR(16) NOT NULL,
    records_processed BIGINT      NOT NULL DEFAULT 0,
    error_message     TEXT
);

CREATE INDEX idx_collection_run_source ON collection_run (source, started_at DESC);
