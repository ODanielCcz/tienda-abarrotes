CREATE VIEW inventory.current_stock AS
SELECT sb.stock_balance_id,
       sb.warehouse_id,
       w.branch_id,
       sb.product_presentation_id,
       pp.sku,
       pp.name AS presentation_name,
       sb.on_hand_quantity,
       sb.reserved_quantity,
       sb.allocated_quantity,
       sb.available_quantity,
       sb.average_unit_cost,
       sb.version,
       sb.updated_at
FROM inventory.stock_balances sb
JOIN organization.warehouses w ON w.warehouse_id = sb.warehouse_id
JOIN catalog.product_presentations pp ON pp.product_presentation_id = sb.product_presentation_id;

CREATE VIEW inventory.lots_near_expiration AS
SELECT l.lot_id,
       l.product_presentation_id,
       l.lot_number,
       l.expires_at,
       lb.warehouse_id,
       lb.available_quantity,
       (l.expires_at - CURRENT_DATE) AS days_to_expiration
FROM inventory.lots l
JOIN inventory.lot_balances lb ON lb.lot_id = l.lot_id
WHERE l.status = 'ACTIVE'
  AND l.expires_at IS NOT NULL
  AND lb.available_quantity > 0;

CREATE VIEW sales.customer_credit_summary AS
SELECT ca.credit_account_id,
       ca.customer_id,
       ca.credit_limit,
       ca.current_balance,
       ca.credit_limit - ca.current_balance AS available_credit,
       ca.status,
       ca.updated_at
FROM sales.credit_accounts ca;

CREATE INDEX idx_business_events_aggregate ON audit.business_events(aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX idx_security_events_user_date ON audit.user_security_events(user_id, created_at DESC);
CREATE INDEX idx_database_principal_events_role_date ON audit.database_principal_events(role_name_snapshot, created_at DESC);
CREATE INDEX idx_stock_movement_items_product ON inventory.stock_movement_items(product_presentation_id, created_at DESC);
CREATE INDEX idx_sales_order_items_product ON sales.sales_order_items(product_presentation_id);

GRANT USAGE ON SCHEMA organization, iam, catalog, inventory, purchasing, sales, cash, billing, sync TO tienda_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA organization, iam, catalog, inventory, purchasing, sales, cash, billing, sync TO tienda_app;
GRANT SELECT ON ALL TABLES IN SCHEMA organization, iam, catalog, inventory, purchasing, sales, cash, billing, sync TO tienda_readonly;

GRANT USAGE ON SCHEMA audit TO tienda_app, tienda_audit_reader;
GRANT INSERT ON audit.business_events, audit.user_security_events, audit.sync_events, audit.data_access_events TO tienda_app;
GRANT SELECT ON ALL TABLES IN SCHEMA audit TO tienda_audit_reader;
GRANT SELECT ON iam.database_principals TO tienda_audit_reader;

ALTER DEFAULT PRIVILEGES IN SCHEMA organization, iam, catalog, inventory, purchasing, sales, cash, billing, sync
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tienda_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA organization, iam, catalog, inventory, purchasing, sales, cash, billing, sync
    GRANT SELECT ON TABLES TO tienda_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA audit
    GRANT SELECT ON TABLES TO tienda_audit_reader;

