INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('ORGANIZATION_BRANCH_READ', 'Consultar sucursales', 'ORGANIZATION', 'Permite consultar y listar sucursales'),
    ('ORGANIZATION_BRANCH_CREATE', 'Crear sucursales', 'ORGANIZATION', 'Permite crear sucursales'),
    ('ORGANIZATION_BRANCH_UPDATE', 'Actualizar sucursales', 'ORGANIZATION', 'Permite actualizar sucursales'),
    ('ORGANIZATION_BRANCH_STATUS', 'Cambiar estado de sucursales', 'ORGANIZATION', 'Permite activar o desactivar sucursales'),
    ('ORGANIZATION_WAREHOUSE_READ', 'Consultar almacenes', 'ORGANIZATION', 'Permite consultar y listar almacenes'),
    ('ORGANIZATION_WAREHOUSE_CREATE', 'Crear almacenes', 'ORGANIZATION', 'Permite crear almacenes'),
    ('ORGANIZATION_WAREHOUSE_UPDATE', 'Actualizar almacenes', 'ORGANIZATION', 'Permite actualizar almacenes'),
    ('ORGANIZATION_WAREHOUSE_STATUS', 'Cambiar estado de almacenes', 'ORGANIZATION', 'Permite activar o desactivar almacenes'),
    ('ORGANIZATION_CASH_REGISTER_READ', 'Consultar cajas registradoras', 'ORGANIZATION', 'Permite consultar y listar cajas registradoras'),
    ('ORGANIZATION_CASH_REGISTER_CREATE', 'Crear cajas registradoras', 'ORGANIZATION', 'Permite crear cajas registradoras'),
    ('ORGANIZATION_CASH_REGISTER_UPDATE', 'Actualizar cajas registradoras', 'ORGANIZATION', 'Permite actualizar cajas registradoras'),
    ('ORGANIZATION_CASH_REGISTER_STATUS', 'Cambiar estado de cajas registradoras', 'ORGANIZATION', 'Permite cambiar estado de cajas registradoras'),
    ('ORGANIZATION_DEVICE_READ', 'Consultar dispositivos', 'ORGANIZATION', 'Permite consultar y listar dispositivos'),
    ('ORGANIZATION_DEVICE_CREATE', 'Crear dispositivos', 'ORGANIZATION', 'Permite crear dispositivos'),
    ('ORGANIZATION_DEVICE_UPDATE', 'Actualizar dispositivos', 'ORGANIZATION', 'Permite actualizar dispositivos'),
    ('ORGANIZATION_DEVICE_STATUS', 'Cambiar estado de dispositivos', 'ORGANIZATION', 'Permite cambiar estado de dispositivos')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'ORGANIZATION_BRANCH_READ',
      'ORGANIZATION_BRANCH_CREATE',
      'ORGANIZATION_BRANCH_UPDATE',
      'ORGANIZATION_BRANCH_STATUS',
      'ORGANIZATION_WAREHOUSE_READ',
      'ORGANIZATION_WAREHOUSE_CREATE',
      'ORGANIZATION_WAREHOUSE_UPDATE',
      'ORGANIZATION_WAREHOUSE_STATUS',
      'ORGANIZATION_CASH_REGISTER_READ',
      'ORGANIZATION_CASH_REGISTER_CREATE',
      'ORGANIZATION_CASH_REGISTER_UPDATE',
      'ORGANIZATION_CASH_REGISTER_STATUS',
      'ORGANIZATION_DEVICE_READ',
      'ORGANIZATION_DEVICE_CREATE',
      'ORGANIZATION_DEVICE_UPDATE',
      'ORGANIZATION_DEVICE_STATUS'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;