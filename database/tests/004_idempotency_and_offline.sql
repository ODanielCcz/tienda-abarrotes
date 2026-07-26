BEGIN;

INSERT INTO sync.inbox_operations
    (operation_id, device_id, device_sequence, idempotency_key, operation_type, aggregate_type, payload, client_created_at)
VALUES
    ('00000000-0000-0000-0000-000000000501',
     '00000000-0000-0000-0000-000000000012',
     1,
     '00000000-0000-0000-0000-000000000502',
     'OFFLINE_SALE',
     'SALES_ORDER',
     '{}'::jsonb,
     clock_timestamp());

DO $$
BEGIN
    BEGIN
        INSERT INTO sync.inbox_operations
            (operation_id, device_id, device_sequence, idempotency_key, operation_type, aggregate_type, payload, client_created_at)
        VALUES
            (gen_random_uuid(),
             '00000000-0000-0000-0000-000000000012',
             2,
             '00000000-0000-0000-0000-000000000502',
             'OFFLINE_SALE',
             'SALES_ORDER',
             '{}'::jsonb,
             clock_timestamp());
        RAISE EXCEPTION 'duplicate idempotency key was accepted';
    EXCEPTION WHEN unique_violation THEN
        NULL;
    END;
END;
$$;

INSERT INTO inventory.device_stock_allocations
    (allocation_id, device_id, warehouse_id, product_presentation_id, assigned_quantity, consumed_quantity, idempotency_key)
VALUES
    ('00000000-0000-0000-0000-000000000503',
     '00000000-0000-0000-0000-000000000012',
     '00000000-0000-0000-0000-000000000011',
     '00000000-0000-0000-0000-000000000151',
     2, 1,
     '00000000-0000-0000-0000-000000000504');

DO $$
BEGIN
    BEGIN
        UPDATE inventory.device_stock_allocations
        SET consumed_quantity = 3
        WHERE allocation_id = '00000000-0000-0000-0000-000000000503';
        RAISE EXCEPTION 'device quota overconsumption was accepted';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END;
$$;

ROLLBACK;

