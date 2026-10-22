ALTER TABLE tb_user DROP CONSTRAINT IF EXISTS tb_user_type_check;

ALTER TABLE tb_user
    ALTER COLUMN type TYPE varchar(32)
    USING CASE type
        WHEN 0 THEN 'ADMINISTRADOR'
        WHEN 1 THEN 'CLIENTE'
        ELSE NULL
    END;