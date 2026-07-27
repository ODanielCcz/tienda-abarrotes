INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('CASH_MOVEMENT_CREATE', 'Registrar movimientos de caja', 'CASH', 'Permite registrar ingresos, retiros y ajustes manuales de caja'),
    ('SALES_PAYMENT_CANCEL', 'Cancelar pagos de ventas', 'SALES', 'Permite anular pagos capturados de ventas')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN ('CASH_MOVEMENT_CREATE', 'SALES_PAYMENT_CANCEL')
ON CONFLICT (role_id, permission_id) DO NOTHING;