\set ON_ERROR_STOP on
BEGIN TRANSACTION READ ONLY;

DO $$
DECLARE
    violation_count BIGINT;
BEGIN
    SELECT count(*) INTO violation_count
    FROM inventory.stock_balances
    WHERE on_hand_quantity < 0
       OR reserved_quantity < 0
       OR allocated_quantity < 0
       OR available_quantity < 0
       OR on_hand_quantity < reserved_quantity + allocated_quantity;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'negative_stock=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM inventory.lot_balances
    WHERE on_hand_quantity < 0
       OR reserved_quantity < 0
       OR allocated_quantity < 0
       OR available_quantity < 0
       OR on_hand_quantity < reserved_quantity + allocated_quantity;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'negative_lot_stock=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM sales.sales_orders orders
    JOIN LATERAL (
        SELECT coalesce(sum(payments.amount), 0) AS captured_amount
        FROM sales.payments payments
        WHERE payments.sales_order_id = orders.sales_order_id
          AND payments.status = 'CAPTURED'
    ) captured ON TRUE
    WHERE orders.branch_id = '90000000-0000-0000-0000-000000000001'::uuid
      AND captured.captured_amount > orders.total;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'payment_overflow=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM (
        SELECT orders.sales_order_id
        FROM sales.sales_orders orders
        LEFT JOIN inventory.stock_movements movements
          ON movements.source_type = 'SALES_ORDER'
         AND movements.source_id = orders.sales_order_id
         AND movements.movement_type = 'SALE'
        WHERE orders.branch_id = '90000000-0000-0000-0000-000000000001'::uuid
          AND orders.status <> 'CANCELLED'
        GROUP BY orders.sales_order_id
        HAVING count(movements.stock_movement_id) <> 1
    ) orphaned;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'orphan_sale_movement=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM (
        SELECT device_id
        FROM sync.inbox_operations
        WHERE status IN ('ACCEPTED', 'DUPLICATE')
          AND device_id::text LIKE '91000000-0000-0000-0001-%'
        GROUP BY device_id
        HAVING min(device_sequence) <> 1
            OR max(device_sequence) <> count(DISTINCT device_sequence)
    ) gaps;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'sync_sequence_gap=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM sync.device_checkpoints checkpoints
    JOIN LATERAL (
        SELECT max(device_sequence) AS maximum_sequence
        FROM sync.inbox_operations operations
        WHERE operations.device_id = checkpoints.device_id
          AND operations.status IN ('ACCEPTED', 'DUPLICATE')
    ) accepted ON accepted.maximum_sequence IS NOT NULL
    WHERE checkpoints.device_id::text LIKE '91000000-0000-0000-0001-%'
      AND (
          checkpoints.last_received_sequence <> accepted.maximum_sequence
          OR checkpoints.last_processed_sequence <> accepted.maximum_sequence
      );
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'sync_checkpoint_mismatch=%', violation_count;
    END IF;

    SELECT count(*) INTO violation_count
    FROM inventory.stock_balances balances
    LEFT JOIN LATERAL (
        SELECT coalesce(sum(items.quantity), 0) AS sold_quantity
        FROM sales.sales_order_items items
        JOIN sales.sales_orders orders ON orders.sales_order_id = items.sales_order_id
        WHERE orders.branch_id = '90000000-0000-0000-0000-000000000001'::uuid
          AND orders.warehouse_id = balances.warehouse_id
          AND items.product_presentation_id = balances.product_presentation_id
          AND orders.status <> 'CANCELLED'
    ) sales ON TRUE
    WHERE balances.warehouse_id = '90000000-0000-0000-0000-000000000011'::uuid
      AND balances.product_presentation_id::text LIKE '95100000-0000-0000-0001-%'
      AND balances.on_hand_quantity <> 10000 - sales.sold_quantity;
    IF violation_count > 0 THEN
        RAISE EXCEPTION 'stock_drift=%', violation_count;
    END IF;
END $$;

SELECT json_build_object(
    'status', 'PASS',
    'negative_stock', 0,
    'payment_overflow', 0,
    'orphan_sale_movement', 0,
    'sync_sequence_gap', 0,
    'sync_checkpoint_mismatch', 0,
    'sales_orders', (
        SELECT count(*) FROM sales.sales_orders
        WHERE branch_id = '90000000-0000-0000-0000-000000000001'::uuid
    ),
    'captured_payments', (
        SELECT count(*) FROM sales.payments payments
        JOIN sales.sales_orders orders ON orders.sales_order_id = payments.sales_order_id
        WHERE orders.branch_id = '90000000-0000-0000-0000-000000000001'::uuid
          AND payments.status = 'CAPTURED'
    ),
    'sync_operations', (
        SELECT count(*) FROM sync.inbox_operations
        WHERE device_id::text LIKE '91000000-0000-0000-0001-%'
    ),
    'carts', (
        SELECT count(*) FROM sales.carts
        WHERE branch_id = '90000000-0000-0000-0000-000000000001'::uuid
    )
) AS invariant_result;

ROLLBACK;
