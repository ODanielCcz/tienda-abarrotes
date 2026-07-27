INSERT INTO iam.permissions (code, name, module, description)
VALUES
 ('SALES_CUSTOMER_READ', 'Consultar clientes', 'SALES', 'Permite consultar clientes de ventas'),
 ('SALES_CUSTOMER_CREATE', 'Crear clientes', 'SALES', 'Permite crear clientes de ventas'),
 ('SALES_CUSTOMER_UPDATE', 'Actualizar clientes', 'SALES', 'Permite actualizar clientes de ventas'),
 ('SALES_CUSTOMER_STATUS', 'Cambiar estado de clientes', 'SALES', 'Permite activar, desactivar o bloquear clientes')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
 AND permission.code IN (
 'SALES_CUSTOMER_READ',
 'SALES_CUSTOMER_CREATE',
 'SALES_CUSTOMER_UPDATE',
 'SALES_CUSTOMER_STATUS'
 )
ON CONFLICT (role_id, permission_id) DO NOTHING;
