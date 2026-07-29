INSERT INTO iam.permissions (code, name, module, description)
VALUES
 ('REPORT_SALES_BY_PERIOD_READ', 'Consultar ventas por periodo', 'REPORTS', 'Permite consultar ventas brutas, devoluciones y ventas netas por periodo'),
 ('REPORT_GROSS_MARGIN_READ', 'Consultar margen y rentabilidad', 'REPORTS', 'Permite consultar margen bruto y rentabilidad por producto'),
 ('REPORT_STOCK_VALUATION_READ', 'Consultar valorizacion de inventario', 'REPORTS', 'Permite consultar el valor del inventario por almacen y presentacion'),
 ('REPORT_EXPIRING_PRODUCTS_READ', 'Consultar productos proximos a caducar', 'REPORTS', 'Permite consultar lotes con existencia proximos a caducar'),
 ('REPORT_RETURNS_READ', 'Consultar reportes de devoluciones', 'REPORTS', 'Permite consultar resumenes de devoluciones confirmadas')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
    'REPORT_SALES_BY_PERIOD_READ',
    'REPORT_GROSS_MARGIN_READ',
    'REPORT_STOCK_VALUATION_READ',
    'REPORT_EXPIRING_PRODUCTS_READ',
    'REPORT_RETURNS_READ'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_returns_confirmed_at
    ON sales.returns (confirmed_at DESC, sales_order_id)
    WHERE status = 'CONFIRMED';

CREATE INDEX IF NOT EXISTS idx_return_items_return_order_item
    ON sales.return_items (return_id, sales_order_item_id);

CREATE INDEX IF NOT EXISTS idx_lots_active_expiration
    ON inventory.lots (expires_at, product_presentation_id)
    WHERE status = 'ACTIVE' AND expires_at IS NOT NULL;
