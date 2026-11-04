CREATE TABLE categories
(
    id         BIGSERIAL    NOT NULL,
    name       VARCHAR(180) NOT NULL,
    slug       VARCHAR(180) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

CREATE TABLE subcategories
(
    id          BIGSERIAL    NOT NULL,
    category_id BIGINT       NOT NULL,
    name        VARCHAR(180) NOT NULL,
    slug        VARCHAR(180) NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_subcategories PRIMARY KEY (id),
    CONSTRAINT fk_subcategories_category
        FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT uq_subcategories_category_name UNIQUE (category_id, name),
    CONSTRAINT uq_subcategories_category_slug UNIQUE (category_id, slug)
);

CREATE FUNCTION update_updated_at_column()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_categories_updated_at
    BEFORE UPDATE ON categories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_subcategories_updated_at
    BEFORE UPDATE ON subcategories
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

INSERT INTO categories (name, slug)
VALUES
    ('Erva-mate', 'erva-mate'),
    ('Cuias', 'cuias'),
    ('Bombas', 'bombas'),
    ('Garrafa térmica', 'garrafa-termica'),
    ('Mateiras', 'mateiras'),
    ('Porta erva-mate', 'porta-erva-mate'),
    ('Acessórios para chimarrão', 'acessorios-para-chimarrao');

INSERT INTO subcategories (category_id, name, slug)
SELECT categories.id, seeds.name, seeds.slug
FROM (
    VALUES
        ('erva-mate', 'Nativa', 'nativa'),
        ('erva-mate', 'Para tereré', 'para-terere'),
        ('erva-mate', 'Com sabores', 'com-sabores'),
        ('erva-mate', 'Moída grossa', 'moida-grossa'),
        ('erva-mate', 'Pura folha', 'pura-folha'),
        ('erva-mate', 'Tradicional', 'tradicional'),
        ('cuias', 'Porongo', 'porongo'),
        ('cuias', 'Madeira', 'madeira'),
        ('cuias', 'Tereré', 'terere'),
        ('bombas', 'Aço comum', 'aco-comum'),
        ('bombas', 'Aço inoxidável', 'aco-inoxidavel'),
        ('bombas', 'Prata', 'prata'),
        ('bombas', 'De alpaca', 'de-alpaca'),
        ('garrafa-termica', 'Termolar', 'termolar'),
        ('garrafa-termica', 'Mokha', 'mokha'),
        ('mateiras', 'Abertas', 'abertas'),
        ('mateiras', 'Fechadas', 'fechadas'),
        ('porta-erva-mate', 'Couro', 'couro'),
        ('porta-erva-mate', 'Lata', 'lata'),
        ('porta-erva-mate', 'Porongo', 'porongo'),
        ('porta-erva-mate', 'Madeira', 'madeira'),
        ('acessorios-para-chimarrao', 'Suportes para cuia', 'suportes-para-cuia'),
        ('acessorios-para-chimarrao', 'Tapa erva', 'tapa-erva'),
        ('acessorios-para-chimarrao', 'Vira-mate', 'vira-mate')
) AS seeds(category_slug, name, slug)
JOIN categories ON categories.slug = seeds.category_slug;

ALTER TABLE tb_product
    ADD COLUMN subcategory_id BIGINT;

DELETE FROM tb_product
WHERE (brand, model, image) IN (
    VALUES
        ('Apple', 'iPhone 15', 'https://example.com/images/iphone-15.jpg'),
        ('Apple', 'iPhone 15 Pro', 'https://example.com/images/iphone-15-pro.jpg'),
        ('Apple', 'iPhone 16', 'https://example.com/images/iphone-16.jpg'),
        ('Samsung', 'Galaxy S24', 'https://example.com/images/galaxy-s24.jpg'),
        ('Samsung', 'Galaxy S24 Ultra', 'https://example.com/images/galaxy-s24-ultra.jpg'),
        ('Samsung', 'Galaxy A55', 'https://example.com/images/galaxy-a55.jpg'),
        ('Google', 'Pixel 8', 'https://example.com/images/pixel-8.jpg'),
        ('Google', 'Pixel 9', 'https://example.com/images/pixel-9.jpg'),
        ('Xiaomi', 'Redmi Note 13 Pro', 'https://example.com/images/redmi-note-13-pro.jpg'),
        ('Xiaomi', 'Xiaomi 14', 'https://example.com/images/xiaomi-14.jpg'),
        ('Motorola', 'Moto G Power', 'https://example.com/images/moto-g-power.jpg'),
        ('Motorola', 'Edge 50 Pro', 'https://example.com/images/edge-50-pro.jpg'),
        ('OnePlus', 'OnePlus 12', 'https://example.com/images/oneplus-12.jpg'),
        ('OnePlus', 'OnePlus Nord CE 4', 'https://example.com/images/oneplus-nord-ce-4.jpg')
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tb_product) THEN
        RAISE EXCEPTION 'Cannot require tb_product.subcategory_id: existing non-demo products need an explicit subcategory mapping';
    END IF;
END;
$$;

ALTER TABLE tb_product
    ALTER COLUMN subcategory_id SET NOT NULL,
    ADD CONSTRAINT fk_tb_product_subcategory
        FOREIGN KEY (subcategory_id) REFERENCES subcategories (id) ON DELETE RESTRICT;

CREATE INDEX idx_tb_product_subcategory_id
    ON tb_product (subcategory_id);
