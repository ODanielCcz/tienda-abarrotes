INSERT INTO iam.permissions (code, name, module, description)
VALUES
 ('REPORT_SALES_READ', 'Consultar reportes de ventas', 'REPORTS', 'Permite consultar reportes operativos de ventas'),
 ('REPORT_INVENTORY_READ', 'Consultar reportes de inventario', 'REPORTS', 'Permite consultar reportes operativos de inventario'),
 ('REPORT_CASH_READ', 'Consultar reportes de caja', 'REPORTS', 'Permite consultar reportes operativos de caja')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
 AND permission.code IN (
 'REPORT_SALES_READ',
 'REPORT_INVENTORY_READ',
 'REPORT_CASH_READ'
 )
ON CONFLICT (role_id, permission_id) DO NOTHING;
