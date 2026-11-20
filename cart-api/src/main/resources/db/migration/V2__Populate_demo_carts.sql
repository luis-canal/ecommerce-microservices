DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM carts
        WHERE id BETWEEN 2001 AND 2006
           OR customer_id IN (102, 110, 115, 108, 117, 107)
    ) THEN
        RAISE EXCEPTION 'Demo cart IDs or customers already exist; refusing to overwrite business data';
    END IF;
END;
$$;

INSERT INTO carts (id, customer_id, version) VALUES
    (2001, 102, 0),
    (2002, 110, 0),
    (2003, 115, 0),
    (2004, 108, 0),
    (2005, 117, 0),
    (2006, 107, 0);

INSERT INTO cart_items (id, cart_id, product_id, desired_quantity) VALUES
    (4001, 2001, 1013, 1),
    (4002, 2001, 1015, 1),
    (4003, 2001, 1001, 2),
    (4004, 2002, 1020, 1),
    (4005, 2002, 1004, 1),
    (4006, 2003, 1014, 1),
    (4007, 2003, 1007, 3),
    (4008, 2004, 1026, 1),
    (4009, 2004, 1022, 1),
    (4010, 2004, 1027, 1),
    (4011, 2004, 1030, 2),
    (4012, 2005, 1005, 4),
    (4013, 2006, 1017, 1),
    (4014, 2006, 1029, 1);

SELECT setval(pg_get_serial_sequence('carts', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM carts), 1), 2006), true);
SELECT setval(pg_get_serial_sequence('cart_items', 'id'), GREATEST(COALESCE((SELECT MAX(id) FROM cart_items), 1), 4014), true);
