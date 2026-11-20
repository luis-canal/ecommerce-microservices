DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM inventory WHERE product_id BETWEEN 1001 AND 1030)
       OR EXISTS (SELECT 1 FROM inventory_reservation WHERE order_id IN (2001, 2002, 2003)) THEN
        RAISE EXCEPTION 'Demo inventory IDs or reservations already exist; refusing to overwrite business data';
    END IF;
END;
$$;

INSERT INTO inventory (
    product_id, product_type, quantity_on_hand, quantity_reserved,
    low_stock_threshold
) VALUES
    (1001, 'MULTI_UNIT', 60, 0, 10),
    (1002, 'MULTI_UNIT', 45, 0, 10),
    (1003, 'MULTI_UNIT', 4, 0, 8),
    (1004, 'MULTI_UNIT', 38, 0, 8),
    (1005, 'MULTI_UNIT', 50, 0, 10),
    (1006, 'MULTI_UNIT', 12, 0, 0),
    (1007, 'MULTI_UNIT', 25, 0, 5),
    (1008, 'MULTI_UNIT', 3, 0, 5),
    (1009, 'SINGLE_UNIT', 0, 0, 0),
    (1010, 'SINGLE_UNIT', 0, 0, 0),
    (1011, 'SINGLE_UNIT', 0, 0, 0),
    (1012, 'SINGLE_UNIT', 0, 0, 0),
    (1013, 'SINGLE_UNIT', 1, 0, 0),
    (1014, 'SINGLE_UNIT', 1, 0, 0),
    (1015, 'MULTI_UNIT', 40, 0, 8),
    (1016, 'MULTI_UNIT', 30, 0, 6),
    (1017, 'MULTI_UNIT', 12, 0, 3),
    (1018, 'MULTI_UNIT', 2, 0, 3),
    (1019, 'MULTI_UNIT', 20, 0, 0),
    (1020, 'MULTI_UNIT', 20, 0, 5),
    (1021, 'MULTI_UNIT', 10, 0, 3),
    (1022, 'MULTI_UNIT', 8, 0, 3),
    (1023, 'MULTI_UNIT', 15, 0, 4),
    (1024, 'MULTI_UNIT', 2, 0, 3),
    (1025, 'MULTI_UNIT', 10, 0, 3),
    (1026, 'MULTI_UNIT', 8, 0, 3),
    (1027, 'MULTI_UNIT', 15, 0, 4),
    (1028, 'MULTI_UNIT', 20, 0, 5),
    (1029, 'MULTI_UNIT', 25, 0, 5),
    (1030, 'MULTI_UNIT', 30, 0, 5);

INSERT INTO inventory_movement (
    product_id, movement_type, quantity_delta, reason, created_at
)
SELECT product_id, 'ENTRY', quantity_delta,
       'Carga inicial fixa da migracao de demonstracao',
       TIMESTAMPTZ '2026-10-01 12:00:00-03'
FROM (
    VALUES
        (1001, 62::BIGINT), (1002, 45::BIGINT), (1003, 4::BIGINT),
        (1004, 38::BIGINT), (1005, 50::BIGINT), (1006, 12::BIGINT),
        (1007, 25::BIGINT), (1008, 3::BIGINT), (1009, 1::BIGINT),
        (1010, 1::BIGINT), (1011, 1::BIGINT), (1012, 1::BIGINT),
        (1013, 1::BIGINT), (1014, 1::BIGINT), (1015, 40::BIGINT),
        (1016, 30::BIGINT), (1017, 12::BIGINT), (1018, 2::BIGINT),
        (1019, 20::BIGINT), (1020, 20::BIGINT), (1021, 10::BIGINT),
        (1022, 8::BIGINT), (1023, 15::BIGINT), (1024, 2::BIGINT),
        (1025, 10::BIGINT), (1026, 8::BIGINT), (1027, 15::BIGINT),
        (1028, 20::BIGINT), (1029, 25::BIGINT), (1030, 30::BIGINT)
) AS entries(product_id, quantity_delta);

INSERT INTO inventory_reservation (
    id, order_id, status, expires_at, created_at, confirmed_at, released_at, expired_at
) VALUES
    (5001, 2001, 'CONFIRMED', TIMESTAMPTZ '2026-10-10 10:20:00-03',
     TIMESTAMPTZ '2026-10-10 10:10:00-03', TIMESTAMPTZ '2026-10-10 10:12:00-03', NULL, NULL),
    (5002, 2002, 'RELEASED', TIMESTAMPTZ '2026-10-12 14:20:00-03',
     TIMESTAMPTZ '2026-10-12 14:10:00-03', NULL, TIMESTAMPTZ '2026-10-12 14:15:00-03', NULL),
    (5003, 2003, 'EXPIRED', TIMESTAMPTZ '2026-10-15 09:20:00-03',
     TIMESTAMPTZ '2026-10-15 09:10:00-03', NULL, NULL, TIMESTAMPTZ '2026-10-15 09:20:00-03');

INSERT INTO inventory_reservation_item (reservation_id, product_id, quantity) VALUES
    (5001, 1001, 2),
    (5001, 1009, 1),
    (5001, 1010, 1),
    (5001, 1011, 1),
    (5001, 1012, 1),
    (5002, 1015, 1),
    (5003, 1016, 1);

INSERT INTO inventory_movement (
    product_id, movement_type, quantity_delta, reason, order_id,
    reservation_id, created_at
) VALUES
    (1001, 'RESERVATION_CONFIRMATION', -2, 'Confirmacao do pedido de demonstracao 2001',
     2001, 5001, TIMESTAMPTZ '2026-10-10 10:12:00-03'),
    (1009, 'RESERVATION_CONFIRMATION', -1, 'Confirmacao do pedido de demonstracao 2001',
     2001, 5001, TIMESTAMPTZ '2026-10-10 10:12:00-03'),
    (1010, 'RESERVATION_CONFIRMATION', -1, 'Confirmacao do pedido de demonstracao 2001',
     2001, 5001, TIMESTAMPTZ '2026-10-10 10:12:00-03'),
    (1011, 'RESERVATION_CONFIRMATION', -1, 'Confirmacao do pedido de demonstracao 2001',
     2001, 5001, TIMESTAMPTZ '2026-10-10 10:12:00-03'),
    (1012, 'RESERVATION_CONFIRMATION', -1, 'Confirmacao do pedido de demonstracao 2001',
     2001, 5001, TIMESTAMPTZ '2026-10-10 10:12:00-03');

SELECT setval(pg_get_serial_sequence('inventory', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM inventory), 1), 30), true);
SELECT setval(pg_get_serial_sequence('inventory_reservation', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM inventory_reservation), 1), 5003), true);
SELECT setval(pg_get_serial_sequence('inventory_reservation_item', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM inventory_reservation_item), 1), 7), true);
SELECT setval(pg_get_serial_sequence('inventory_movement', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM inventory_movement), 1), 34), true);
