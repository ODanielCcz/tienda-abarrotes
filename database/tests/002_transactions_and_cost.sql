BEGIN;

UPDATE inventory.stock_balances
SET on_hand_quantity = 10,
    reserved_quantity = 0,
    allocated_quantity = 0,
    average_unit_cost = 15,
    version = version + 1
WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191';

DO $$
DECLARE
    affected INTEGER;
BEGIN
    BEGIN
        UPDATE inventory.stock_balances
        SET on_hand_quantity = on_hand_quantity - 2
        WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'
          AND available_quantity >= 2;

        UPDATE inventory.stock_balances
        SET on_hand_quantity = on_hand_quantity - 999
        WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'
          AND available_quantity >= 999;
        GET DIAGNOSTICS affected = ROW_COUNT;
        IF affected = 0 THEN
            RAISE EXCEPTION 'insufficient stock';
        END IF;
    EXCEPTION WHEN OTHERS THEN
        NULL;
    END;
END;
$$;

SELECT test_support.assert_true(
    (SELECT on_hand_quantity = 10 FROM inventory.stock_balances WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'),
    'failed multi-line operation must roll back every line'
);

UPDATE inventory.stock_balances
SET average_unit_cost = ((on_hand_quantity * average_unit_cost) + (10 * 25.00)) / (on_hand_quantity + 10),
    on_hand_quantity = on_hand_quantity + 10
WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191';

SELECT test_support.assert_true(
    (SELECT average_unit_cost = 20.0000 AND on_hand_quantity = 20 FROM inventory.stock_balances WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'),
    'weighted average cost must equal 20'
);

ROLLBACK;

