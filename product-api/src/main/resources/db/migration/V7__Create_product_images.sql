CREATE TABLE product_images
(
    id           BIGSERIAL    NOT NULL,
    product_id   INTEGER      NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    content_type VARCHAR(64)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    position     INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES tb_product (id) ON DELETE CASCADE,
    CONSTRAINT uk_product_images_object_key UNIQUE (object_key),
    CONSTRAINT ck_product_images_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_product_images_product_position ON product_images (product_id, position);

-- Garante no máximo uma imagem principal por produto.
CREATE UNIQUE INDEX uk_product_images_primary ON product_images (product_id) WHERE is_primary;
