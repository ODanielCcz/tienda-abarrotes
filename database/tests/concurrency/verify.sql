\set ON_ERROR_STOP on
SELECT test_support.assert_true(
    (SELECT on_hand_quantity = 0 FROM inventory.stock_balances WHERE stock_balance_id = '00000000-0000-0000-0000-000000000191'),
    'stock must finish at zero'
);
SELECT test_support.assert_true(
    (SELECT count(*) = 1 FROM inventory.stock_movements WHERE source_type = 'CONCURRENCY_TEST'),
    'exactly one concurrent sale must win'
);

