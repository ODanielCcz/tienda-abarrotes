\set ON_ERROR_STOP on

\getenv perf_user_password PERF_FIXTURE_USER_PASSWORD

\if :{?max_virtual_users}
\else
\set max_virtual_users 150
\endif
\if :{?non_lot_presentations}
\else
\set non_lot_presentations 150
\endif
\if :{?lot_presentations}
\else
\set lot_presentations 150
\endif
\if :{?lots_per_lot_presentation}
\else
\set lots_per_lot_presentation 2
\endif
\if :{?customer_count}
\else
\set customer_count 200
\endif
\if :{?supplier_count}
\else
\set supplier_count 20
\endif
\if :{?mobile_device_count}
\else
\set mobile_device_count 150
\endif
\if :{?cash_register_count}
\else
\set cash_register_count 16
\endif

BEGIN;

SELECT set_config('tienda.fixture.max_virtual_users', :'max_virtual_users', true);
SELECT set_config('tienda.fixture.non_lot_presentations', :'non_lot_presentations', true);
SELECT set_config('tienda.fixture.lot_presentations', :'lot_presentations', true);
SELECT set_config('tienda.fixture.lots_per_lot_presentation', :'lots_per_lot_presentation', true);
SELECT set_config('tienda.fixture.customer_count', :'customer_count', true);
SELECT set_config('tienda.fixture.supplier_count', :'supplier_count', true);
SELECT set_config('tienda.fixture.mobile_device_count', :'mobile_device_count', true);
SELECT set_config('tienda.fixture.cash_register_count', :'cash_register_count', true);

INSERT INTO organization.branches (
    branch_id, code, name, legal_name, timezone, currency_code, status
) VALUES (
    '90000000-0000-0000-0000-000000000001',
    'PERF',
    'Sucursal sintética de rendimiento',
    'Datos sintéticos sin valor comercial',
    'America/Mexico_City',
    'MXN',
    'ACTIVE'
)
ON CONFLICT (branch_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    legal_name = EXCLUDED.legal_name,
    timezone = EXCLUDED.timezone,
    currency_code = EXCLUDED.currency_code,
    status = EXCLUDED.status,
    updated_at = clock_timestamp();

INSERT INTO organization.warehouses (
    warehouse_id, branch_id, code, name, warehouse_type, status
) VALUES
    (
        '90000000-0000-0000-0000-000000000011',
        '90000000-0000-0000-0000-000000000001',
        'PERF-VENTA',
        'Almacén de venta sintético',
        'STORE',
        'ACTIVE'
    ),
    (
        '90000000-0000-0000-0000-000000000012',
        '90000000-0000-0000-0000-000000000001',
        'PERF-RESPALDO',
        'Almacén de respaldo sintético',
        'BACKROOM',
        'ACTIVE'
    )
ON CONFLICT (warehouse_id) DO UPDATE SET
    branch_id = EXCLUDED.branch_id,
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    warehouse_type = EXCLUDED.warehouse_type,
    status = EXCLUDED.status,
    updated_at = clock_timestamp();

INSERT INTO organization.devices (
    device_id, branch_id, warehouse_id, device_code, device_type,
    platform, app_version, status
)
SELECT
    ('91000000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '90000000-0000-0000-0000-000000000001'::uuid,
    CASE WHEN series_id % 2 = 1
        THEN '90000000-0000-0000-0000-000000000011'::uuid
        ELSE '90000000-0000-0000-0000-000000000012'::uuid
    END,
    'PERF-MOBILE-' || lpad(series_id::text, 3, '0'),
    'MOBILE_EMPLOYEE',
    'K6',
    '1.0.0-performance',
    'ACTIVE'
FROM generate_series(1, :mobile_device_count) AS fixture(series_id)
ON CONFLICT (device_id) DO UPDATE SET
    branch_id = EXCLUDED.branch_id,
    warehouse_id = EXCLUDED.warehouse_id,
    device_code = EXCLUDED.device_code,
    device_type = EXCLUDED.device_type,
    platform = EXCLUDED.platform,
    app_version = EXCLUDED.app_version,
    status = EXCLUDED.status,
    updated_at = clock_timestamp();

INSERT INTO organization.cash_registers (
    cash_register_id, branch_id, code, name, status
)
SELECT
    ('92000000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '90000000-0000-0000-0000-000000000001'::uuid,
    'PERF-CAJA-' || lpad(series_id::text, 2, '0'),
    'Caja sintética ' || lpad(series_id::text, 2, '0'),
    'ACTIVE'
FROM generate_series(1, :cash_register_count) AS fixture(series_id)
ON CONFLICT (cash_register_id) DO UPDATE SET
    branch_id = EXCLUDED.branch_id,
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    status = EXCLUDED.status;

INSERT INTO iam.roles (
    role_id, code, name, description, is_system, status
) VALUES (
    '93000000-0000-0000-0000-000000000001',
    'PERF_OPERATOR',
    'Operador restringido de rendimiento',
    'Rol sintético con acceso operativo únicamente a la sucursal PERF',
    FALSE,
    'ACTIVE'
)
ON CONFLICT (role_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_system = FALSE,
    status = 'ACTIVE';

INSERT INTO iam.users (
    user_id, username, email, password_hash, display_name, status,
    failed_login_attempts, locked_until, auth_version
) VALUES (
    '93000000-0000-0000-0000-000000000101',
    'perf_operator',
    'perf-operator@example.invalid',
    crypt(:'perf_user_password', gen_salt('bf', 10)),
    'Operador sintético de rendimiento',
    'ACTIVE',
    0,
    NULL,
    0
)
ON CONFLICT (user_id) DO UPDATE SET
    username = EXCLUDED.username,
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    status = 'ACTIVE',
    failed_login_attempts = 0,
    locked_until = NULL,
    auth_version = iam.users.auth_version,
    updated_at = clock_timestamp();

INSERT INTO iam.user_roles (
    user_role_id, user_id, role_id, branch_id
) VALUES (
    '93000000-0000-0000-0000-000000000201',
    '93000000-0000-0000-0000-000000000101',
    '93000000-0000-0000-0000-000000000001',
    '90000000-0000-0000-0000-000000000001'
)
ON CONFLICT (user_role_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    role_id = EXCLUDED.role_id,
    branch_id = EXCLUDED.branch_id,
    valid_until = NULL;

INSERT INTO iam.user_branch_access (user_id, branch_id, status)
VALUES (
    '93000000-0000-0000-0000-000000000101',
    '90000000-0000-0000-0000-000000000001',
    'ACTIVE'
)
ON CONFLICT (user_id, branch_id) DO UPDATE SET status = 'ACTIVE';

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT
    '93000000-0000-0000-0000-000000000001'::uuid,
    permission.permission_id
FROM iam.permissions permission
-- PERF_PERMISSION_INSERT_ALLOWLIST_BEGIN
WHERE permission.code IN (
    'CATALOG_BRAND_READ',
    'CATALOG_CATEGORY_READ',
    'CATALOG_PRESENTATION_READ',
    'CATALOG_PRODUCT_READ',
    'INVENTORY_LOT_READ',
    'INVENTORY_MOVEMENT_READ',
    'INVENTORY_STOCK_READ',
    'ORGANIZATION_BRANCH_READ',
    'ORGANIZATION_CASH_REGISTER_READ',
    'ORGANIZATION_DEVICE_READ',
    'ORGANIZATION_WAREHOUSE_READ',
    'PURCHASING_PURCHASE_READ',
    'PURCHASING_SUPPLIER_READ',
    'REPORT_CASH_READ',
    'REPORT_EXPIRING_PRODUCTS_READ',
    'REPORT_GROSS_MARGIN_READ',
    'REPORT_INVENTORY_READ',
    'REPORT_RETURNS_READ',
    'REPORT_SALES_BY_PERIOD_READ',
    'REPORT_SALES_READ',
    'REPORT_STOCK_VALUATION_READ',
    'SALES_CUSTOMER_READ',
    'SALES_ORDER_CREATE',
    'SALES_ORDER_READ',
    'SALES_PAYMENT_CREATE',
    'SALES_PAYMENT_READ',
    'SYNC_CHECKPOINT_ACK',
    'SYNC_CHECKPOINT_READ',
    'SYNC_CONFLICT_READ',
    'SYNC_INBOX_WRITE',
    'SYNC_OUTBOX_READ'
)
-- PERF_PERMISSION_INSERT_ALLOWLIST_END
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO sync.device_user_bindings (device_id, user_id)
SELECT
    ('91000000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '93000000-0000-0000-0000-000000000101'::uuid
FROM generate_series(1, :mobile_device_count) AS fixture(series_id)
ON CONFLICT (device_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    bound_at = clock_timestamp();

INSERT INTO catalog.units_of_measure (
    unit_id, code, name, symbol, quantity_scale, sat_unit_code
) VALUES (
    '94000000-0000-0000-0000-000000000001',
    'PERF_PZA',
    'Pieza sintética',
    'pza',
    0,
    'H87'
)
ON CONFLICT (unit_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    symbol = EXCLUDED.symbol,
    quantity_scale = EXCLUDED.quantity_scale,
    sat_unit_code = EXCLUDED.sat_unit_code;

INSERT INTO catalog.categories (category_id, code, name, status)
VALUES (
    '94000000-0000-0000-0000-000000000002',
    'PERF_LOAD',
    'Categoría sintética de rendimiento',
    'ACTIVE'
)
ON CONFLICT (category_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    status = 'ACTIVE',
    updated_at = clock_timestamp();

INSERT INTO catalog.brands (brand_id, code, name, status)
VALUES (
    '94000000-0000-0000-0000-000000000003',
    'PERF_SYNTHETIC',
    'Marca sintética de rendimiento',
    'ACTIVE'
)
ON CONFLICT (brand_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    status = 'ACTIVE';

INSERT INTO catalog.taxes (tax_id, code, name, rate, sat_tax_code, status)
VALUES (
    '94000000-0000-0000-0000-000000000004',
    'PERF_IVA16',
    'IVA sintético 16%',
    0.16,
    '002',
    'ACTIVE'
)
ON CONFLICT (tax_id) DO UPDATE SET
    code = EXCLUDED.code,
    name = EXCLUDED.name,
    rate = EXCLUDED.rate,
    sat_tax_code = EXCLUDED.sat_tax_code,
    status = 'ACTIVE';

INSERT INTO catalog.price_lists (
    price_list_id, code, name, currency_code, status
) VALUES (
    '94000000-0000-0000-0000-000000000005',
    'GENERAL',
    'Precio general',
    'MXN',
    'ACTIVE'
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    currency_code = EXCLUDED.currency_code,
    status = 'ACTIVE';

INSERT INTO purchasing.suppliers (
    supplier_id, supplier_code, legal_name, trade_name, tax_id,
    email, phone, credit_days, status
)
SELECT
    ('99000000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    'PERF-SUP-' || lpad(series_id::text, 3, '0'),
    'Proveedor sintético ' || lpad(series_id::text, 3, '0') || ' SA de CV',
    'Proveedor PERF ' || lpad(series_id::text, 3, '0'),
    'XAXX010101000',
    'supplier-' || series_id || '@example.invalid',
    '+520000' || lpad(series_id::text, 6, '0'),
    series_id % 31,
    'ACTIVE'
FROM generate_series(1, :supplier_count) AS fixture(series_id)
ON CONFLICT (supplier_id) DO UPDATE SET
    supplier_code = EXCLUDED.supplier_code,
    legal_name = EXCLUDED.legal_name,
    trade_name = EXCLUDED.trade_name,
    tax_id = EXCLUDED.tax_id,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    credit_days = EXCLUDED.credit_days,
    status = 'ACTIVE',
    updated_at = clock_timestamp();

INSERT INTO sales.customers (
    customer_id, customer_code, customer_type, display_name, email, phone, status
)
SELECT
    ('98000000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    'PERF-CUST-' || lpad(series_id::text, 4, '0'),
    CASE WHEN series_id % 5 = 0 THEN 'BUSINESS' ELSE 'PERSON' END,
    'Cliente sintético ' || lpad(series_id::text, 4, '0'),
    'customer-' || series_id || '@example.invalid',
    '+521000' || lpad(series_id::text, 6, '0'),
    'ACTIVE'
FROM generate_series(1, :customer_count) AS fixture(series_id)
ON CONFLICT (customer_id) DO UPDATE SET
    customer_code = EXCLUDED.customer_code,
    customer_type = EXCLUDED.customer_type,
    display_name = EXCLUDED.display_name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    status = 'ACTIVE',
    updated_at = clock_timestamp();

INSERT INTO catalog.products (
    product_id, category_id, brand_id, name, description, product_type,
    tracks_inventory, tracks_lots, tracks_expiration, status,
    sat_product_service_code
)
SELECT
    ('94100000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '94000000-0000-0000-0000-000000000002'::uuid,
    '94000000-0000-0000-0000-000000000003'::uuid,
    'Producto sin lote PERF ' || lpad(series_id::text, 3, '0'),
    'Dato sintético exclusivo para el VU ' || series_id,
    'GOODS',
    TRUE,
    FALSE,
    FALSE,
    'ACTIVE',
    '50181900'
FROM generate_series(1, :non_lot_presentations) AS fixture(series_id)
ON CONFLICT (product_id) DO UPDATE SET
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    product_type = EXCLUDED.product_type,
    tracks_inventory = TRUE,
    tracks_lots = FALSE,
    tracks_expiration = FALSE,
    status = 'ACTIVE',
    sat_product_service_code = EXCLUDED.sat_product_service_code,
    updated_at = clock_timestamp();

INSERT INTO catalog.products (
    product_id, category_id, brand_id, name, description, product_type,
    tracks_inventory, tracks_lots, tracks_expiration, status,
    sat_product_service_code
)
SELECT
    ('94200000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '94000000-0000-0000-0000-000000000002'::uuid,
    '94000000-0000-0000-0000-000000000003'::uuid,
    'Producto con lote PERF ' || lpad(series_id::text, 3, '0'),
    'Dato sintético FEFO exclusivo para el VU ' || series_id,
    'GOODS',
    TRUE,
    TRUE,
    TRUE,
    'ACTIVE',
    '50181900'
FROM generate_series(1, :lot_presentations) AS fixture(series_id)
ON CONFLICT (product_id) DO UPDATE SET
    category_id = EXCLUDED.category_id,
    brand_id = EXCLUDED.brand_id,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    product_type = EXCLUDED.product_type,
    tracks_inventory = TRUE,
    tracks_lots = TRUE,
    tracks_expiration = TRUE,
    status = 'ACTIVE',
    sat_product_service_code = EXCLUDED.sat_product_service_code,
    updated_at = clock_timestamp();

INSERT INTO catalog.product_presentations (
    product_presentation_id, product_id, unit_id, tax_id, sku, name,
    conversion_factor, net_content, minimum_stock, status
)
SELECT
    ('95100000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    ('94100000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '94000000-0000-0000-0000-000000000001'::uuid,
    '94000000-0000-0000-0000-000000000004'::uuid,
    'PERF-NL-' || lpad(series_id::text, 3, '0'),
    'Presentación sin lote PERF ' || lpad(series_id::text, 3, '0'),
    1,
    1,
    100,
    'ACTIVE'
FROM generate_series(1, :non_lot_presentations) AS fixture(series_id)
ON CONFLICT (product_presentation_id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    unit_id = EXCLUDED.unit_id,
    tax_id = EXCLUDED.tax_id,
    sku = EXCLUDED.sku,
    name = EXCLUDED.name,
    conversion_factor = EXCLUDED.conversion_factor,
    net_content = EXCLUDED.net_content,
    minimum_stock = EXCLUDED.minimum_stock,
    status = 'ACTIVE',
    updated_at = clock_timestamp();

INSERT INTO catalog.product_presentations (
    product_presentation_id, product_id, unit_id, tax_id, sku, name,
    conversion_factor, net_content, minimum_stock, status
)
SELECT
    ('95200000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    ('94200000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    '94000000-0000-0000-0000-000000000001'::uuid,
    '94000000-0000-0000-0000-000000000004'::uuid,
    'PERF-LT-' || lpad(series_id::text, 3, '0'),
    'Presentación con lote PERF ' || lpad(series_id::text, 3, '0'),
    1,
    1,
    100,
    'ACTIVE'
FROM generate_series(1, :lot_presentations) AS fixture(series_id)
ON CONFLICT (product_presentation_id) DO UPDATE SET
    product_id = EXCLUDED.product_id,
    unit_id = EXCLUDED.unit_id,
    tax_id = EXCLUDED.tax_id,
    sku = EXCLUDED.sku,
    name = EXCLUDED.name,
    conversion_factor = EXCLUDED.conversion_factor,
    net_content = EXCLUDED.net_content,
    minimum_stock = EXCLUDED.minimum_stock,
    status = 'ACTIVE',
    updated_at = clock_timestamp();

INSERT INTO catalog.prices (
    price_id, price_list_id, branch_id, product_presentation_id,
    amount, valid_from, valid_until, created_by
)
SELECT
    ('95300000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    (SELECT price_list_id FROM catalog.price_lists WHERE code = 'GENERAL'),
    '90000000-0000-0000-0000-000000000001'::uuid,
    ('95100000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    20 + (series_id % 25),
    '2020-01-01T00:00:00Z'::timestamptz,
    NULL,
    '93000000-0000-0000-0000-000000000101'::uuid
FROM generate_series(1, :non_lot_presentations) AS fixture(series_id)
ON CONFLICT (price_id) DO UPDATE SET
    price_list_id = EXCLUDED.price_list_id,
    branch_id = EXCLUDED.branch_id,
    product_presentation_id = EXCLUDED.product_presentation_id,
    amount = EXCLUDED.amount,
    valid_from = EXCLUDED.valid_from,
    valid_until = NULL,
    created_by = EXCLUDED.created_by;

INSERT INTO catalog.prices (
    price_id, price_list_id, branch_id, product_presentation_id,
    amount, valid_from, valid_until, created_by
)
SELECT
    ('95400000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    (SELECT price_list_id FROM catalog.price_lists WHERE code = 'GENERAL'),
    NULL,
    ('95200000-0000-0000-0001-' || lpad(series_id::text, 12, '0'))::uuid,
    30 + (series_id % 25),
    '2020-01-01T00:00:00Z'::timestamptz,
    NULL,
    '93000000-0000-0000-0000-000000000101'::uuid
FROM generate_series(1, :lot_presentations) AS fixture(series_id)
ON CONFLICT (price_id) DO UPDATE SET
    price_list_id = EXCLUDED.price_list_id,
    branch_id = NULL,
    product_presentation_id = EXCLUDED.product_presentation_id,
    amount = EXCLUDED.amount,
    valid_from = EXCLUDED.valid_from,
    valid_until = NULL,
    created_by = EXCLUDED.created_by;

INSERT INTO inventory.lots (
    lot_id, product_presentation_id, supplier_id, lot_number,
    manufactured_at, expires_at, status
)
SELECT
    (
        '96000000-0000-0001-' || lpad(presentation_id::text, 4, '0') || '-' ||
        lpad(lot_ordinal::text, 12, '0')
    )::uuid,
    ('95200000-0000-0000-0001-' || lpad(presentation_id::text, 12, '0'))::uuid,
    (
        '99000000-0000-0000-0001-' ||
        lpad((((presentation_id - 1) % :supplier_count) + 1)::text, 12, '0')
    )::uuid,
    'PERF-' || lpad(presentation_id::text, 3, '0') || '-' || lpad(lot_ordinal::text, 2, '0'),
    CASE WHEN lot_ordinal = 1 THEN DATE '2025-01-01' ELSE DATE '2025-06-01' END,
    CASE WHEN lot_ordinal = 1 THEN DATE '2035-01-15' ELSE DATE '2035-06-15' END,
    'ACTIVE'
FROM generate_series(1, :lot_presentations) AS presentation(presentation_id)
CROSS JOIN generate_series(1, :lots_per_lot_presentation) AS lot(lot_ordinal)
ON CONFLICT (lot_id) DO UPDATE SET
    product_presentation_id = EXCLUDED.product_presentation_id,
    supplier_id = EXCLUDED.supplier_id,
    lot_number = EXCLUDED.lot_number,
    manufactured_at = EXCLUDED.manufactured_at,
    expires_at = EXCLUDED.expires_at,
    status = 'ACTIVE';

WITH fixture_warehouses AS (
    SELECT * FROM (VALUES
        (1, '90000000-0000-0000-0000-000000000011'::uuid, 10000::numeric),
        (2, '90000000-0000-0000-0000-000000000012'::uuid, 2000::numeric)
    ) AS warehouse(ordinal, warehouse_id, quantity)
)
INSERT INTO inventory.stock_balances (
    stock_balance_id, warehouse_id, product_presentation_id,
    on_hand_quantity, reserved_quantity, allocated_quantity,
    average_unit_cost, version
)
SELECT
    (
        '97000000-0000-0001-' || lpad(warehouse.ordinal::text, 4, '0') || '-' ||
        lpad(presentation_id::text, 12, '0')
    )::uuid,
    warehouse.warehouse_id,
    ('95100000-0000-0000-0001-' || lpad(presentation_id::text, 12, '0'))::uuid,
    warehouse.quantity,
    0,
    0,
    8 + (presentation_id % 10),
    0
FROM generate_series(1, :non_lot_presentations) AS presentation(presentation_id)
CROSS JOIN fixture_warehouses warehouse
ON CONFLICT (stock_balance_id) DO NOTHING;

WITH fixture_warehouses AS (
    SELECT * FROM (VALUES
        (1, '90000000-0000-0000-0000-000000000011'::uuid, 10000::numeric),
        (2, '90000000-0000-0000-0000-000000000012'::uuid, 2000::numeric)
    ) AS warehouse(ordinal, warehouse_id, quantity)
)
INSERT INTO inventory.stock_balances (
    stock_balance_id, warehouse_id, product_presentation_id,
    on_hand_quantity, reserved_quantity, allocated_quantity,
    average_unit_cost, version
)
SELECT
    (
        '97000000-0000-0002-' || lpad(warehouse.ordinal::text, 4, '0') || '-' ||
        lpad(presentation_id::text, 12, '0')
    )::uuid,
    warehouse.warehouse_id,
    ('95200000-0000-0000-0001-' || lpad(presentation_id::text, 12, '0'))::uuid,
    warehouse.quantity,
    0,
    0,
    12 + (presentation_id % 10),
    0
FROM generate_series(1, :lot_presentations) AS presentation(presentation_id)
CROSS JOIN fixture_warehouses warehouse
ON CONFLICT (stock_balance_id) DO NOTHING;

WITH fixture_warehouses AS (
    SELECT * FROM (VALUES
        (1, '90000000-0000-0000-0000-000000000011'::uuid, 5000::numeric),
        (2, '90000000-0000-0000-0000-000000000012'::uuid, 1000::numeric)
    ) AS warehouse(ordinal, warehouse_id, quantity_per_lot)
)
INSERT INTO inventory.lot_balances (
    lot_balance_id, warehouse_id, lot_id, on_hand_quantity,
    reserved_quantity, allocated_quantity, version
)
SELECT
    (
        '97000000-0000-0003-' || lpad(warehouse.ordinal::text, 4, '0') || '-' ||
        lpad((((presentation_id - 1) * :lots_per_lot_presentation) + lot_ordinal)::text, 12, '0')
    )::uuid,
    warehouse.warehouse_id,
    (
        '96000000-0000-0001-' || lpad(presentation_id::text, 4, '0') || '-' ||
        lpad(lot_ordinal::text, 12, '0')
    )::uuid,
    warehouse.quantity_per_lot,
    0,
    0,
    0
FROM generate_series(1, :lot_presentations) AS presentation(presentation_id)
CROSS JOIN generate_series(1, :lots_per_lot_presentation) AS lot(lot_ordinal)
CROSS JOIN fixture_warehouses warehouse
ON CONFLICT (lot_balance_id) DO NOTHING;

DO $fixture_assertions$
DECLARE
    expected_vus integer := current_setting('tienda.fixture.max_virtual_users')::integer;
    expected_non_lot integer := current_setting('tienda.fixture.non_lot_presentations')::integer;
    expected_lot integer := current_setting('tienda.fixture.lot_presentations')::integer;
    expected_lots_per_presentation integer := current_setting('tienda.fixture.lots_per_lot_presentation')::integer;
    expected_customers integer := current_setting('tienda.fixture.customer_count')::integer;
    expected_suppliers integer := current_setting('tienda.fixture.supplier_count')::integer;
    expected_devices integer := current_setting('tienda.fixture.mobile_device_count')::integer;
    expected_registers integer := current_setting('tienda.fixture.cash_register_count')::integer;
BEGIN
    IF expected_non_lot < expected_vus OR expected_lot < expected_vus OR expected_devices < expected_vus THEN
        RAISE EXCEPTION 'El fixture no separa datos para todos los VU';
    END IF;

    IF (SELECT count(*) FROM organization.warehouses
        WHERE branch_id = '90000000-0000-0000-0000-000000000001') <> 2 THEN
        RAISE EXCEPTION 'Se esperaban exactamente dos almacenes PERF';
    END IF;

    IF (SELECT count(*) FROM organization.devices
        WHERE device_code LIKE 'PERF-MOBILE-%' AND status = 'ACTIVE') <> expected_devices THEN
        RAISE EXCEPTION 'Cantidad inesperada de dispositivos PERF';
    END IF;

    IF (SELECT count(*) FROM sync.device_user_bindings binding
        JOIN organization.devices device ON device.device_id = binding.device_id
        WHERE binding.user_id = '93000000-0000-0000-0000-000000000101'
          AND device.device_code LIKE 'PERF-MOBILE-%') <> expected_devices THEN
        RAISE EXCEPTION 'Todos los dispositivos PERF deben pertenecer al usuario restringido';
    END IF;

    IF (SELECT count(*) FROM organization.cash_registers
        WHERE branch_id = '90000000-0000-0000-0000-000000000001') <> expected_registers THEN
        RAISE EXCEPTION 'Cantidad inesperada de cajas PERF';
    END IF;

    IF (SELECT count(*) FROM catalog.product_presentations WHERE sku LIKE 'PERF-NL-%') <> expected_non_lot THEN
        RAISE EXCEPTION 'Cantidad inesperada de presentaciones sin lote';
    END IF;

    IF (SELECT count(*) FROM catalog.product_presentations WHERE sku LIKE 'PERF-LT-%') <> expected_lot THEN
        RAISE EXCEPTION 'Cantidad inesperada de presentaciones con lote';
    END IF;

    IF (SELECT count(*) FROM inventory.lots WHERE lot_number LIKE 'PERF-%')
       <> expected_lot * expected_lots_per_presentation THEN
        RAISE EXCEPTION 'Cantidad inesperada de lotes PERF';
    END IF;

    IF (SELECT count(*) FROM sales.customers WHERE customer_code LIKE 'PERF-CUST-%') <> expected_customers THEN
        RAISE EXCEPTION 'Cantidad inesperada de clientes PERF';
    END IF;

    IF (SELECT count(*) FROM purchasing.suppliers WHERE supplier_code LIKE 'PERF-SUP-%') <> expected_suppliers THEN
        RAISE EXCEPTION 'Cantidad inesperada de proveedores PERF';
    END IF;

    -- PERF_EXACT_ROLE_SCOPE_ASSERTION
    IF (
        SELECT count(*)
        FROM iam.user_roles
        WHERE user_id = '93000000-0000-0000-0000-000000000101'
    ) <> 1 OR NOT EXISTS (
        SELECT 1
        FROM iam.user_roles user_role
        JOIN iam.roles role ON role.role_id = user_role.role_id
        WHERE user_role.user_id = '93000000-0000-0000-0000-000000000101'
          AND user_role.role_id = '93000000-0000-0000-0000-000000000001'
          AND user_role.branch_id = '90000000-0000-0000-0000-000000000001'
          AND user_role.valid_until IS NULL
          AND role.code = 'PERF_OPERATOR'
          AND role.status = 'ACTIVE'
          AND role.is_system = FALSE
    ) THEN
        RAISE EXCEPTION 'perf_operator debe tener únicamente PERF_OPERATOR en la sucursal PERF';
    END IF;

    -- PERF_EXACT_BRANCH_SCOPE_ASSERTION
    IF (
        SELECT count(*)
        FROM iam.user_branch_access
        WHERE user_id = '93000000-0000-0000-0000-000000000101'
    ) <> 1 OR NOT EXISTS (
        SELECT 1
        FROM iam.user_branch_access access
        JOIN iam.users user_account ON user_account.user_id = access.user_id
        JOIN organization.branches branch ON branch.branch_id = access.branch_id
        WHERE access.user_id = '93000000-0000-0000-0000-000000000101'
          AND access.branch_id = '90000000-0000-0000-0000-000000000001'
          AND access.status = 'ACTIVE'
          AND user_account.status = 'ACTIVE'
          AND branch.status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'perf_operator debe tener acceso activo únicamente a la sucursal PERF';
    END IF;

    -- PERF_EXACT_PERMISSION_SCOPE_ASSERTION
    IF EXISTS (
        WITH expected_permission(code) AS (
            -- PERF_PERMISSION_ASSERT_ALLOWLIST_BEGIN
            VALUES
                ('CATALOG_BRAND_READ'),
                ('CATALOG_CATEGORY_READ'),
                ('CATALOG_PRESENTATION_READ'),
                ('CATALOG_PRODUCT_READ'),
                ('INVENTORY_LOT_READ'),
                ('INVENTORY_MOVEMENT_READ'),
                ('INVENTORY_STOCK_READ'),
                ('ORGANIZATION_BRANCH_READ'),
                ('ORGANIZATION_CASH_REGISTER_READ'),
                ('ORGANIZATION_DEVICE_READ'),
                ('ORGANIZATION_WAREHOUSE_READ'),
                ('PURCHASING_PURCHASE_READ'),
                ('PURCHASING_SUPPLIER_READ'),
                ('REPORT_CASH_READ'),
                ('REPORT_EXPIRING_PRODUCTS_READ'),
                ('REPORT_GROSS_MARGIN_READ'),
                ('REPORT_INVENTORY_READ'),
                ('REPORT_RETURNS_READ'),
                ('REPORT_SALES_BY_PERIOD_READ'),
                ('REPORT_SALES_READ'),
                ('REPORT_STOCK_VALUATION_READ'),
                ('SALES_CUSTOMER_READ'),
                ('SALES_ORDER_CREATE'),
                ('SALES_ORDER_READ'),
                ('SALES_PAYMENT_CREATE'),
                ('SALES_PAYMENT_READ'),
                ('SYNC_CHECKPOINT_ACK'),
                ('SYNC_CHECKPOINT_READ'),
                ('SYNC_CONFLICT_READ'),
                ('SYNC_INBOX_WRITE'),
                ('SYNC_OUTBOX_READ')
            -- PERF_PERMISSION_ASSERT_ALLOWLIST_END
        ),
        assigned_permission(code) AS (
            SELECT permission.code
            FROM iam.role_permissions role_permission
            JOIN iam.permissions permission
              ON permission.permission_id = role_permission.permission_id
            WHERE role_permission.role_id = '93000000-0000-0000-0000-000000000001'
        )
        SELECT 1
        FROM (
            (
                SELECT code FROM expected_permission
                EXCEPT
                SELECT code FROM assigned_permission
            )
            UNION ALL
            (
                SELECT code FROM assigned_permission
                EXCEPT
                SELECT code FROM expected_permission
            )
        ) mismatch
    ) THEN
        RAISE EXCEPTION 'Los permisos de PERF_OPERATOR no coinciden exactamente con la allowlist';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM catalog.product_presentations presentation
        WHERE presentation.sku LIKE 'PERF-%'
          AND NOT EXISTS (
              SELECT 1
              FROM catalog.prices price
              JOIN catalog.price_lists price_list ON price_list.price_list_id = price.price_list_id
              WHERE price.product_presentation_id = presentation.product_presentation_id
                AND price_list.code = 'GENERAL'
                AND price_list.status = 'ACTIVE'
                AND price.valid_from <= clock_timestamp()
                AND (price.valid_until IS NULL OR price.valid_until > clock_timestamp())
          )
    ) THEN
        RAISE EXCEPTION 'Cada presentación PERF debe tener un precio GENERAL vigente';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inventory.stock_balances balance
        WHERE balance.on_hand_quantity < 0 OR balance.available_quantity < 0
    ) THEN
        RAISE EXCEPTION 'El fixture no puede producir stock negativo';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inventory.stock_balances balance
        JOIN catalog.product_presentations presentation
          ON presentation.product_presentation_id = balance.product_presentation_id
        LEFT JOIN (
            SELECT lot_balance.warehouse_id, lot.product_presentation_id,
                   sum(lot_balance.on_hand_quantity) AS lot_quantity
            FROM inventory.lot_balances lot_balance
            JOIN inventory.lots lot ON lot.lot_id = lot_balance.lot_id
            GROUP BY lot_balance.warehouse_id, lot.product_presentation_id
        ) lots ON lots.warehouse_id = balance.warehouse_id
              AND lots.product_presentation_id = balance.product_presentation_id
        WHERE presentation.sku LIKE 'PERF-LT-%'
          AND balance.on_hand_quantity <> coalesce(lots.lot_quantity, 0)
    ) THEN
        RAISE EXCEPTION 'El stock general y el stock por lote PERF deben coincidir';
    END IF;
END
$fixture_assertions$;

COMMIT;

\echo 'Fixtures deterministas de rendimiento cargados correctamente.'
