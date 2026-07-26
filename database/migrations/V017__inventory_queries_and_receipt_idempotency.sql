ALTER TABLE inventory.stock_movements
    ADD COLUMN source_fingerprint VARCHAR(64);

CREATE INDEX idx_stock_movements_idempotency_source
    ON inventory.stock_movements (idempotency_key, source_type)
    WHERE idempotency_key IS NOT NULL;

INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('INVENTORY_STOCK_READ', 'Consultar stock', 'INVENTORY', 'Permite consultar existencias por almacen y presentacion'),
    ('INVENTORY_LOT_READ', 'Consultar lotes', 'INVENTORY', 'Permite consultar lotes y caducidades'),
    ('INVENTORY_PALLET_READ', 'Consultar pallets', 'INVENTORY', 'Permite consultar pallets e items recibidos'),
    ('INVENTORY_MOVEMENT_READ', 'Consultar movimientos de inventario', 'INVENTORY', 'Permite consultar movimientos append-only de inventario')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code IN ('SYSTEM_ADMIN')
  AND permission.code IN (
      'INVENTORY_STOCK_READ',
      'INVENTORY_LOT_READ',
      'INVENTORY_PALLET_READ',
      'INVENTORY_MOVEMENT_READ'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;
