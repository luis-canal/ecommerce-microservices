-- Somente demonstração: as peças únicas 1009 a 1012 saem zeradas da V2 (vendidas no pedido
-- 2001). Repõe uma unidade de cada para que todo produto ativo do catálogo de demonstração
-- possa ser comprado logo após a primeira subida, sem rodar script. Registros que já têm saldo
-- ou reserva não são alterados.
WITH restocked AS (
    UPDATE inventory
    SET quantity_on_hand = 1,
        updated_at = CURRENT_TIMESTAMP
    WHERE product_id IN (1009, 1010, 1011, 1012)
      AND quantity_on_hand = 0
      AND quantity_reserved = 0
    RETURNING product_id
)
INSERT INTO inventory_movement (product_id, movement_type, quantity_delta, reason)
SELECT product_id, 'ENTRY', 1, 'Reposicao das pecas unicas de demonstracao'
FROM restocked;
