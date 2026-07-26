INSERT INTO iam.permissions (code, name, module, description)
VALUES
    ('CATALOG_PRESENTATION_READ', 'Consultar presentaciones', 'CATALOG', 'Permite consultar presentaciones de productos'),
    ('CATALOG_PRESENTATION_CREATE', 'Crear presentaciones', 'CATALOG', 'Permite registrar presentaciones de productos'),
    ('CATALOG_PRESENTATION_UPDATE', 'Actualizar presentaciones', 'CATALOG', 'Permite modificar presentaciones de productos'),
    ('CATALOG_PRESENTATION_STATUS', 'Cambiar estado de presentaciones', 'CATALOG', 'Permite activar, desactivar o descontinuar presentaciones'),
    ('INVENTORY_RECEIPT_READ', 'Consultar recepciones', 'INVENTORY', 'Permite consultar recepciones de inventario'),
    ('INVENTORY_RECEIPT_CREATE', 'Crear recepciones', 'INVENTORY', 'Permite registrar recepciones de inventario')
ON CONFLICT (code) DO NOTHING;

INSERT INTO iam.role_permissions (role_id, permission_id)
SELECT role.role_id, permission.permission_id
FROM iam.roles role
CROSS JOIN iam.permissions permission
WHERE role.code IN ('SYSTEM_ADMIN', 'CATALOG_MANAGER')
  AND permission.code IN (
      'CATALOG_PRESENTATION_READ',
      'CATALOG_PRESENTATION_CREATE',
      'CATALOG_PRESENTATION_UPDATE',
      'CATALOG_PRESENTATION_STATUS',
      'INVENTORY_RECEIPT_READ',
      'INVENTORY_RECEIPT_CREATE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE inventory.pallets (
    pallet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    warehouse_id UUID NOT NULL REFERENCES organization.warehouses(warehouse_id),
    stock_movement_id UUID REFERENCES inventory.stock_movements(stock_movement_id),
    pallet_code VARCHAR(80) NOT NULL,
    external_pallet_code VARCHAR(120),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED','MOVED','DEPLETED','CANCELLED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (warehouse_id, pallet_code)
);

CREATE TABLE inventory.pallet_items (
    pallet_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pallet_id UUID NOT NULL REFERENCES inventory.pallets(pallet_id),
    product_presentation_id UUID NOT NULL REFERENCES catalog.product_presentations(product_presentation_id),
    lot_id UUID REFERENCES inventory.lots(lot_id),
    quantity NUMERIC(18,3) NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_pallets_warehouse_status ON inventory.pallets (warehouse_id, status);
CREATE INDEX idx_pallets_movement ON inventory.pallets (stock_movement_id);
CREATE INDEX idx_pallet_items_pallet ON inventory.pallet_items (pallet_id);
CREATE INDEX idx_pallet_items_lot ON inventory.pallet_items (lot_id);
CREATE INDEX idx_pallet_items_presentation ON inventory.pallet_items (product_presentation_id);
