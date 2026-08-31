\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

SELECT jsonb_build_object(
    'databaseName', current_database(),
    'databaseUser', current_user,
    'stock', (
        SELECT jsonb_build_object(
            'rowCount', count(*),
            'availableQuantity', COALESCE(max(available_quantity), 0)
        )
        FROM inventory.stock_balances
        WHERE warehouse_id = '90000000-0000-0000-0000-000000000011'::uuid
          AND product_presentation_id = '95100000-0000-0000-0001-000000000001'::uuid
    ),
    'salesOrders', (SELECT count(*) FROM sales.sales_orders),
    'payments', (SELECT count(*) FROM sales.payments),
    'syncOperations', (SELECT count(*) FROM sync.inbox_operations),
    'carts', (SELECT count(*) FROM sales.carts)
)::text;
