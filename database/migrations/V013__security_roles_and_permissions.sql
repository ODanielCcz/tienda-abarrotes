INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('CATALOG_BRAND_READ', 'Consultar marcas', 'CATALOG', 'Permite consultar y listar marcas'),
    ('CATALOG_BRAND_CREATE', 'Crear marcas', 'CATALOG', 'Permite registrar nuevas marcas'),
    ('CATALOG_BRAND_UPDATE', 'Actualizar marcas', 'CATALOG', 'Permite modificar código y nombre de marcas'),
    ('CATALOG_BRAND_STATUS', 'Cambiar estado de marcas', 'CATALOG', 'Permite activar o desactivar marcas')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.roles (code, name, description, is_system)
VALUES
    ('SYSTEM_ADMIN', 'Administrador del sistema', 'Acceso administrativo transversal', TRUE),
    ('CATALOG_MANAGER', 'Responsable de catálogo', 'Administración del catálogo comercial', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code IN ('SYSTEM_ADMIN', 'CATALOG_MANAGER')
  AND permission.code IN (
      'CATALOG_BRAND_READ',
      'CATALOG_BRAND_CREATE',
      'CATALOG_BRAND_UPDATE',
      'CATALOG_BRAND_STATUS'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username_ci
    ON iam.users (LOWER(username));
