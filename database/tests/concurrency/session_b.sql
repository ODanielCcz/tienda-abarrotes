\set ON_ERROR_STOP on
BEGIN;
WITH won AS (
    UPDATE inventory.stock_balances
    SET on_hand_quantity = on_hand_quantity - 1, version = version + 1
    WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'
      AND available_quantity >= 1
    RETURNING on_hand_quantity + 1 AS before_qty, on_hand_quantity AS after_qty
), movement AS (
    INSERT INTO inventory.stock_movements (stock_movement_id, branch_id, warehouse_id, movement_type, status, source_type, source_id, reason)
    SELECT '00000000-0000-0000-0000-000000000402',
           '00000000-0000-0000-0000-000000000001',
           '00000000-0000-0000-0000-000000000011',
           'SALE', 'CONFIRMED', 'CONCURRENCY_TEST', gen_random_uuid(), 'Session B won'
    FROM won
    RETURNING stock_movement_id
)
INSERT INTO inventory.stock_movement_items
    (stock_movement_id, product_presentation_id, direction, quantity, unit_cost, quantity_before, quantity_after)
SELECT m.stock_movement_id,
       '00000000-0000-0000-0000-000000000151',
       'OUT', 1, 15, w.before_qty, w.after_qty
FROM movement m CROSS JOIN won w;
COMMIT;

