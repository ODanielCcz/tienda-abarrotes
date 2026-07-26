INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('PURCHASING_SUPPLIER_READ', 'Consultar proveedores', 'PURCHASING', 'Permite consultar proveedores'),
    ('PURCHASING_SUPPLIER_CREATE', 'Crear proveedores', 'PURCHASING', 'Permite crear proveedores'),
    ('PURCHASING_SUPPLIER_UPDATE', 'Actualizar proveedores', 'PURCHASING', 'Permite actualizar proveedores'),
    ('PURCHASING_SUPPLIER_STATUS', 'Cambiar estado de proveedores', 'PURCHASING', 'Permite activar, desactivar o bloquear proveedores'),
    ('PURCHASING_PURCHASE_READ', 'Consultar compras', 'PURCHASING', 'Permite consultar compras'),
    ('PURCHASING_PURCHASE_CREATE', 'Crear compras', 'PURCHASING', 'Permite crear compras'),
    ('PURCHASING_PURCHASE_CONFIRM', 'Confirmar compras', 'PURCHASING', 'Permite confirmar compras'),
    ('PURCHASING_PURCHASE_RECEIVE', 'Recibir compras', 'PURCHASING', 'Permite recibir mercancia de compras')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'PURCHASING_SUPPLIER_READ',
      'PURCHASING_SUPPLIER_CREATE',
      'PURCHASING_SUPPLIER_UPDATE',
      'PURCHASING_SUPPLIER_STATUS',
      'PURCHASING_PURCHASE_READ',
      'PURCHASING_PURCHASE_CREATE',
      'PURCHASING_PURCHASE_CONFIRM',
      'PURCHASING_PURCHASE_RECEIVE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
