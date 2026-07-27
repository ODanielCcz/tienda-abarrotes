INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('SALES_RETURN_READ', 'Consultar devoluciones de ventas', 'SALES', 'Permite consultar devoluciones de ventas'),
    ('SALES_RETURN_CREATE', 'Crear devoluciones de ventas', 'SALES', 'Permite crear borradores de devoluciones de ventas'),
    ('SALES_RETURN_CONFIRM', 'Confirmar devoluciones de ventas', 'SALES', 'Permite confirmar devoluciones y reponer inventario'),
    ('SALES_RETURN_CANCEL', 'Cancelar devoluciones de ventas', 'SALES', 'Permite cancelar borradores de devoluciones de ventas')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'SALES_RETURN_READ',
      'SALES_RETURN_CREATE',
      'SALES_RETURN_CONFIRM',
      'SALES_RETURN_CANCEL'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;