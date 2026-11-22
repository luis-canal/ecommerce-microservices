ALTER TABLE tb_order
    ALTER COLUMN order_date TYPE TIMESTAMPTZ
        USING order_date AT TIME ZONE 'UTC',
    ADD COLUMN status VARCHAR(32),
    ADD COLUMN reservation_id BIGINT,
    ADD COLUMN reservation_expires_at TIMESTAMPTZ,
    ADD COLUMN confirmed_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD COLUMN request_fingerprint VARCHAR(64);

UPDATE tb_order
SET status = 'LEGACY'
WHERE status IS NULL;

ALTER TABLE tb_order
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'LEGACY',
    ADD CONSTRAINT ck_tb_order_status CHECK (
        status IN ('RESERVATION_PENDING', 'PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'FAILED', 'LEGACY')
    );

ALTER TABLE tb_order_item
    ALTER COLUMN price_at_purchase TYPE NUMERIC(19, 2)
        USING ROUND(price_at_purchase::NUMERIC, 2),
    ADD COLUMN product_name VARCHAR(200),
    ADD COLUMN product_brand VARCHAR(200),
    ADD COLUMN product_model VARCHAR(200),
    ADD COLUMN product_description VARCHAR(2000),
    ADD COLUMN product_image VARCHAR(1000);

UPDATE tb_order_item
SET product_name = 'Produto ' || product_id
WHERE product_name IS NULL;

ALTER TABLE tb_order_item
    ADD CONSTRAINT ck_tb_order_item_quantity CHECK (quantity > 0);

ALTER TABLE tb_order_item
    ADD CONSTRAINT ck_tb_order_item_order_id_present CHECK (order_id IS NOT NULL) NOT VALID;

CREATE UNIQUE INDEX ux_tb_order_customer_idempotency_key
    ON tb_order (customer_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX ix_tb_order_customer_date
    ON tb_order (customer_id, order_date DESC, id DESC);

CREATE INDEX ix_tb_order_status_expiration
    ON tb_order (status, reservation_expires_at)
    WHERE status = 'PENDING_PAYMENT';

CREATE INDEX ix_tb_order_item_order_id
    ON tb_order_item (order_id);
