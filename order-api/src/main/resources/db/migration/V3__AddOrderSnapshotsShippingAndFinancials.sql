ALTER TABLE tb_order
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN shipping_zip_code VARCHAR(9),
    ADD COLUMN shipping_street VARCHAR(200),
    ADD COLUMN shipping_number VARCHAR(32),
    ADD COLUMN shipping_complement VARCHAR(200),
    ADD COLUMN shipping_neighborhood VARCHAR(120),
    ADD COLUMN shipping_city VARCHAR(120),
    ADD COLUMN shipping_state VARCHAR(2),
    ADD COLUMN subtotal NUMERIC(19, 2),
    ADD COLUMN discount_amount NUMERIC(19, 2),
    ADD COLUMN shipping_amount NUMERIC(19, 2),
    ADD COLUMN total_amount NUMERIC(19, 2),
    ADD COLUMN currency VARCHAR(3);

ALTER TABLE tb_order_item
    ADD COLUMN discount_amount NUMERIC(19, 2),
    ADD COLUMN subtotal NUMERIC(19, 2),
    ADD COLUMN catalog_price_at_purchase NUMERIC(19, 2),
    ADD COLUMN catalog_currency_at_purchase VARCHAR(3),
    ADD COLUMN category_id BIGINT,
    ADD COLUMN category_name VARCHAR(200),
    ADD COLUMN subcategory_id BIGINT,
    ADD COLUMN subcategory_name VARCHAR(200),
    ADD COLUMN product_type VARCHAR(32),
    ADD COLUMN product_active BOOLEAN,
    ADD COLUMN product_environment VARCHAR(200),
    ADD COLUMN promotion_message VARCHAR(500);

ALTER TABLE tb_order_item
    ALTER COLUMN product_name TYPE TEXT,
    ALTER COLUMN product_brand TYPE TEXT,
    ALTER COLUMN product_model TYPE TEXT,
    ALTER COLUMN product_description TYPE TEXT,
    ALTER COLUMN product_image TYPE TEXT,
    ALTER COLUMN category_name TYPE TEXT,
    ALTER COLUMN subcategory_name TYPE TEXT,
    ALTER COLUMN product_environment TYPE TEXT,
    ALTER COLUMN promotion_message TYPE TEXT;

UPDATE tb_order_item
SET discount_amount = 0,
    subtotal = ROUND(price_at_purchase * quantity, 2),
    catalog_price_at_purchase = price_at_purchase,
    catalog_currency_at_purchase = currency_at_purchase;

UPDATE tb_order
SET updated_at = order_date,
    subtotal = COALESCE((
        SELECT SUM(item.subtotal)
        FROM tb_order_item item
        WHERE item.order_id = tb_order.id
    ), 0),
    discount_amount = 0,
    shipping_amount = 0,
    total_amount = COALESCE((
        SELECT SUM(item.subtotal)
        FROM tb_order_item item
        WHERE item.order_id = tb_order.id
    ), 0),
    currency = 'BRL';

ALTER TABLE tb_order
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN discount_amount SET NOT NULL,
    ALTER COLUMN shipping_amount SET NOT NULL,
    ALTER COLUMN total_amount SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN subtotal SET DEFAULT 0,
    ALTER COLUMN discount_amount SET DEFAULT 0,
    ALTER COLUMN shipping_amount SET DEFAULT 0,
    ALTER COLUMN total_amount SET DEFAULT 0,
    ALTER COLUMN currency SET DEFAULT 'BRL';

ALTER TABLE tb_order_item
    ALTER COLUMN discount_amount SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN discount_amount SET DEFAULT 0,
    ALTER COLUMN subtotal SET DEFAULT 0;

ALTER TABLE tb_order
    ADD CONSTRAINT ck_tb_order_amounts_nonnegative CHECK (
        subtotal >= 0 AND discount_amount >= 0 AND discount_amount <= subtotal
        AND shipping_amount >= 0 AND total_amount >= 0
        AND total_amount = subtotal - discount_amount + shipping_amount
    );

ALTER TABLE tb_order_item
    ADD CONSTRAINT ck_tb_order_item_amounts_nonnegative CHECK (
        discount_amount >= 0 AND subtotal >= 0
    );

CREATE INDEX ix_tb_order_updated_at ON tb_order (updated_at, id);
CREATE INDEX ix_tb_order_customer_confirmed
    ON tb_order (customer_id, confirmed_at DESC, id DESC);
