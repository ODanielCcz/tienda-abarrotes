INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('INVENTORY_ADJUSTMENT_CREATE', 'Crear ajustes de inventario', 'INVENTORY', 'Permite crear ajustes manuales de inventario'),
    ('INVENTORY_TRANSFER_CREATE', 'Crear traspasos de inventario', 'INVENTORY', 'Permite crear traspasos entre almacenes'),
    ('INVENTORY_COUNT_CREATE', 'Crear conteos fisicos', 'INVENTORY', 'Permite crear conteos fisicos de inventario'),
    ('INVENTORY_COUNT_CONFIRM', 'Confirmar conteos fisicos', 'INVENTORY', 'Permite confirmar conteos fisicos y generar ajustes'),
    ('INVENTORY_RESERVATION_CREATE', 'Crear reservas de inventario', 'INVENTORY', 'Permite reservar stock disponible'),
    ('INVENTORY_RESERVATION_RELEASE', 'Liberar reservas de inventario', 'INVENTORY', 'Permite liberar reservas activas'),
    ('INVENTORY_EXPIRING_LOT_READ', 'Consultar lotes por caducar', 'INVENTORY', 'Permite consultar lotes proximos a caducar')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code = 'SYSTEM_ADMIN'
  AND permission.code IN (
      'INVENTORY_ADJUSTMENT_CREATE',
      'INVENTORY_TRANSFER_CREATE',
      'INVENTORY_COUNT_CREATE',
      'INVENTORY_COUNT_CONFIRM',
      'INVENTORY_RESERVATION_CREATE',
      'INVENTORY_RESERVATION_RELEASE',
      'INVENTORY_EXPIRING_LOT_READ'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
