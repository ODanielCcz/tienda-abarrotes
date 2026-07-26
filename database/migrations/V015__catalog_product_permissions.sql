INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('CATALOG_PRODUCT_READ', 'Consultar productos', 'CATALOG', 'Permite consultar y listar productos'),
    ('CATALOG_PRODUCT_CREATE', 'Crear productos', 'CATALOG', 'Permite registrar nuevos productos'),
    ('CATALOG_PRODUCT_UPDATE', 'Actualizar productos', 'CATALOG', 'Permite modificar datos comerciales de productos'),
    ('CATALOG_PRODUCT_STATUS', 'Cambiar estado de productos', 'CATALOG', 'Permite activar, desactivar o descontinuar productos')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code IN ('SYSTEM_ADMIN', 'CATALOG_MANAGER')
  AND permission.code IN (
      'CATALOG_PRODUCT_READ',
      'CATALOG_PRODUCT_CREATE',
      'CATALOG_PRODUCT_UPDATE',
      'CATALOG_PRODUCT_STATUS'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_products_brand_status
    ON catalog.products (brand_id, status);

CREATE INDEX IF NOT EXISTS idx_products_status_name
    ON catalog.products (status, name);
