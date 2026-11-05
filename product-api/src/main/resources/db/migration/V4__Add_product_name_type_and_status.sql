ALTER TABLE tb_product
    ADD COLUMN name VARCHAR(255),
    ADD COLUMN product_type VARCHAR(32),
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE tb_product
SET name = NULLIF(BTRIM(model), '')
WHERE name IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM tb_product
        WHERE name IS NULL OR product_type IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot require tb_product.name and product_type: existing products need a valid name and an explicit SINGLE_UNIT or MULTI_UNIT mapping';
    END IF;
END;
$$;

ALTER TABLE tb_product
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN product_type SET NOT NULL,
    ADD CONSTRAINT ck_tb_product_product_type
        CHECK (product_type IN ('SINGLE_UNIT', 'MULTI_UNIT'));
