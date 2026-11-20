DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tb_order WHERE id BETWEEN 2001 AND 2003)
       OR EXISTS (SELECT 1 FROM tb_order WHERE idempotency_key LIKE 'demo-flyway-%') THEN
        RAISE EXCEPTION 'Demo orders or idempotency keys already exist; refusing to overwrite business data';
    END IF;
END;
$$;

INSERT INTO tb_order (
    id, order_date, customer_id, status, reservation_id, reservation_expires_at,
    confirmed_at, cancelled_at, idempotency_key, request_fingerprint, updated_at,
    shipping_zip_code, shipping_street, shipping_number, shipping_complement,
    shipping_neighborhood, shipping_city, shipping_state,
    subtotal, discount_amount, shipping_amount, total_amount, currency
) VALUES
    (2001, TIMESTAMPTZ '2026-10-10 10:10:00-03', 101, 'CONFIRMED', 5001,
     TIMESTAMPTZ '2026-10-10 10:20:00-03', TIMESTAMPTZ '2026-10-10 10:12:00-03',
     NULL, 'demo-flyway-order-2001', 'demo-flyway-confirmed-2001',
     TIMESTAMPTZ '2026-10-10 10:12:00-03',
     '99010-000', 'Rua Independência', '742', 'Apto 301', 'Centro', 'Passo Fundo', 'RS',
     1189.40, 0, 0, 1189.40, 'BRL'),
    (2002, TIMESTAMPTZ '2026-10-12 14:10:00-03', 102, 'CANCELLED', 5002,
     TIMESTAMPTZ '2026-10-12 14:20:00-03', NULL,
     TIMESTAMPTZ '2026-10-12 14:15:00-03', 'demo-flyway-order-2002', 'demo-flyway-cancelled-2002',
     TIMESTAMPTZ '2026-10-12 14:15:00-03',
     '90050-170', 'Rua José do Patrocínio', '1180', NULL, 'Cidade Baixa', 'Porto Alegre', 'RS',
     39.90, 0, 0, 39.90, 'BRL'),
    (2003, TIMESTAMPTZ '2026-10-15 09:10:00-03', 103, 'EXPIRED', 5003,
     TIMESTAMPTZ '2026-10-15 09:20:00-03', NULL, NULL,
     'demo-flyway-order-2003', 'demo-flyway-expired-2003',
     TIMESTAMPTZ '2026-10-15 09:20:00-03',
     '95020-000', 'Rua Sinimbu', '2045', 'Casa', 'Centro', 'Caxias do Sul', 'RS',
     49.90, 0, 0, 49.90, 'BRL');

INSERT INTO tb_order_item (
    id, product_id, quantity, price_at_purchase, currency_at_purchase, order_id,
    product_name, product_brand, product_model, product_description, product_image,
    discount_amount, subtotal, catalog_price_at_purchase, catalog_currency_at_purchase,
    category_id, category_name, subcategory_id, subcategory_name,
    product_type, product_active, product_environment
) VALUES
    (3001, 1001, 2, 24.90, 'BRL', 2001,
     'Erva-mate Barão Tradicional 1 kg', 'Barão de Cotegipe', 'Tradicional 1 kg',
     'Erva-mate tradicional para chimarrão, moagem média e sabor equilibrado. Pacote de 1 kg.',
     NULL, 0, 49.80, 24.90, 'BRL',
     NULL, 'Erva-mate', NULL, 'Tradicional', 'MULTI_UNIT', TRUE, 'demo-flyway'),
    (3002, 1009, 1, 189.90, 'BRL', 2001,
     'Cuia de Porongo com Bocal de Alpaca', 'Artesanato Missioneiro', 'Porongo Bocal Alpaca',
     'Cuia de porongo curada, peça única, com bocal de alpaca cinzelado à mão.',
     NULL, 0, 189.90, 189.90, 'BRL',
     NULL, 'Cuias', NULL, 'Porongo', 'SINGLE_UNIT', TRUE, 'demo-flyway'),
    (3005, 1010, 1, 69.90, 'BRL', 2001,
     'Cuia de Porongo Lisa Tradicional', 'Artesanato Missioneiro', 'Porongo Lisa',
     'Cuia de porongo lisa e curada, formato tradicional gaúcho. Peça única.',
     NULL, 0, 69.90, 69.90, 'BRL',
     NULL, 'Cuias', NULL, 'Porongo', 'SINGLE_UNIT', TRUE, 'demo-flyway'),
    (3006, 1011, 1, 129.90, 'BRL', 2001,
     'Cuia de Porongo Revestida em Couro', 'Gaúcha Artesanal', 'Porongo Couro',
     'Cuia de porongo revestida em couro legítimo com costura aparente. Peça única.',
     NULL, 0, 129.90, 129.90, 'BRL',
     NULL, 'Cuias', NULL, 'Porongo', 'SINGLE_UNIT', TRUE, 'demo-flyway'),
    (3007, 1012, 1, 749.90, 'BRL', 2001,
     'Cuia de Porongo com Bocal de Prata 950', 'Prataria Gaúcha', 'Porongo Bocal Prata 950',
     'Cuia de porongo selecionado com bocal e base em prata 950 trabalhada. Peça única de coleção.',
     NULL, 0, 749.90, 749.90, 'BRL',
     NULL, 'Cuias', NULL, 'Porongo', 'SINGLE_UNIT', TRUE, 'demo-flyway'),
    (3003, 1015, 1, 39.90, 'BRL', 2002,
     'Bomba Inox com Mola', 'Bombas Campeiras', 'Inox 304 Mola',
     'Bomba em aço inox 304 com filtro de mola removível, fácil de limpar.',
     NULL, 0, 39.90, 39.90, 'BRL',
     NULL, 'Bombas', NULL, 'Aço inoxidável', 'MULTI_UNIT', TRUE, 'demo-flyway'),
    (3004, 1016, 1, 49.90, 'BRL', 2003,
     'Bomba Inox Desmontável', 'Bombas Campeiras', 'Inox Desmontável',
     'Bomba de inox com bojo desmontável por rosca, para higienização completa.',
     NULL, 0, 49.90, 49.90, 'BRL',
     NULL, 'Bombas', NULL, 'Aço inoxidável', 'MULTI_UNIT', TRUE, 'demo-flyway');

SELECT setval(pg_get_serial_sequence('tb_order', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM tb_order), 1), 2003), true);
SELECT setval(pg_get_serial_sequence('tb_order_item', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM tb_order_item), 1), 3007), true);
