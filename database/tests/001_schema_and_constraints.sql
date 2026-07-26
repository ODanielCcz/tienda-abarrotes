BEGIN;

SELECT test_support.assert_true(to_regclass('inventory.stock_balances') IS NOT NULL, 'stock_balances must exist');
SELECT test_support.assert_true(to_regclass('audit.database_principal_events') IS NOT NULL, 'database audit table must exist');
SELECT test_support.assert_true(to_regclass('billing.fiscal_documents') IS NOT NULL, 'billing table must exist');
SELECT test_support.assert_true(EXISTS (SELECT 1 FROM audit.unregistered_database_roles WHERE role_name IS NOT NULL) OR NOT EXISTS (SELECT 1 FROM audit.unregistered_database_roles), 'role reconciliation view must be queryable');

DO $$
BEGIN
    BEGIN
        INSERT INTO inventory.stock_balances (warehouse_id, product_presentation_id, on_hand_quantity, reserved_quantity)
        VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000151', 1, 2);
        RAISE EXCEPTION 'negative availability was accepted';
    EXCEPTION WHEN check_violation OR unique_violation THEN
        NULL;
    END;
END;
$$;

ROLLBACK;

