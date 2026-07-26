INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('CATALOG_CATEGORY_READ', 'Consultar categorias', 'CATALOG', 'Permite consultar y listar categorias'),
    ('CATALOG_CATEGORY_CREATE', 'Crear categorias', 'CATALOG', 'Permite registrar nuevas categorias'),
    ('CATALOG_CATEGORY_UPDATE', 'Actualizar categorias', 'CATALOG', 'Permite modificar codigo, nombre y categoria padre'),
    ('CATALOG_CATEGORY_STATUS', 'Cambiar estado de categorias', 'CATALOG', 'Permite activar o desactivar categorias')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code IN ('SYSTEM_ADMIN', 'CATALOG_MANAGER')
  AND permission.code IN (
      'CATALOG_CATEGORY_READ',
      'CATALOG_CATEGORY_CREATE',
      'CATALOG_CATEGORY_UPDATE',
      'CATALOG_CATEGORY_STATUS'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_categories_parent_status
    ON catalog.categories (parent_category_id, status);

CREATE INDEX IF NOT EXISTS idx_categories_status_name
    ON catalog.categories (status, name);