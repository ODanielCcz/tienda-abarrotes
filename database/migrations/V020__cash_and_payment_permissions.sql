ALTER TABLE sales.payments
 ADD COLUMN IF NOT EXISTS source_fingerprint VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_payments_idempotency_fingerprint
 ON sales.payments (idempotency_key, source_fingerprint);

INSERT INTO organization.cash_registers (
 cash_register_id, branch_id, code, name, status
)
SELECT
 '00000000-0000-0000-0000-000000000021',
 branch.branch_id,
 'LOCAL-01',
 'Caja local 01',
 'ACTIVE'
FROM organization.branches branch
WHERE branch.branch_id = '00000000-0000-0000-0000-000000000001'
ON CONFLICT (branch_id, code) DO NOTHING;

INSERT INTO iam.permissions (code, name, module, description)
VALUES
 ('CASH_SESSION_READ', 'Consultar sesiones de caja', 'CASH', 'Permite consultar aperturas y cierres de caja'),
 ('CASH_SESSION_OPEN', 'Abrir caja', 'CASH', 'Permite abrir una sesion de caja'),
 ('CASH_SESSION_CLOSE', 'Cerrar caja', 'CASH', 'Permite cerrar una sesion de caja'),
 ('CASH_MOVEMENT_READ', 'Consultar movimientos de caja', 'CASH', 'Permite consultar movimientos de efectivo'),
 ('SALES_PAYMENT_READ', 'Consultar pagos de ventas', 'SALES', 'Permite consultar pagos asociados a ventas'),
 ('SALES_PAYMENT_CREATE', 'Registrar pagos de ventas', 'SALES', 'Permite registrar pagos asociados a ventas')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
 AND permission.code IN (
 'CASH_SESSION_READ',
 'CASH_SESSION_OPEN',
 'CASH_SESSION_CLOSE',
 'CASH_MOVEMENT_READ',
 'SALES_PAYMENT_READ',
 'SALES_PAYMENT_CREATE'
 )
ON CONFLICT (role_id, permission_id) DO NOTHING;
