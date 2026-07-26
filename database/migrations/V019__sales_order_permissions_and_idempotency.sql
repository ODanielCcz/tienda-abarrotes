ALTER TABLE sales.sales_orders
    ADD COLUMN source_fingerprint VARCHAR(64);

CREATE INDEX idx_sales_orders_idempotency_fingerprint
    ON sales.sales_orders (idempotency_key, source_fingerprint);

INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('SALES_ORDER_READ', 'Consultar ventas', 'SALES', 'Permite consultar ordenes de venta'),
    ('SALES_ORDER_CREATE', 'Crear ventas', 'SALES', 'Permite crear ordenes de venta y descontar inventario'),
    ('SALES_ORDER_CANCEL', 'Cancelar ventas', 'SALES', 'Permite cancelar ventas y reponer inventario')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN ('SALES_ORDER_READ', 'SALES_ORDER_CREATE', 'SALES_ORDER_CANCEL')
ON CONFLICT (role_id, permission_id) DO NOTHING;
