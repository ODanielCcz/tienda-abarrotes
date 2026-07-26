\set ON_ERROR_STOP on
BEGIN;
DELETE FROM inventory.stock_movement_items WHERE stock_movement_id IN (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000402'
);
DELETE FROM inventory.stock_movements WHERE stock_movement_id IN (
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000402'
);
UPDATE inventory.stock_balances
SET on_hand_quantity = 1, reserved_quantity = 0, allocated_quantity = 0, version = version + 1
WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191';
COMMIT;

