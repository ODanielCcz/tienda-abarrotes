\set ON_ERROR_STOP on

BEGIN;

INSERT INTO organization.branches (branch_id, code, name, timezone, currency_code)
VALUES ('00000000-0000-0000-0000-000000000001', 'CENTRAL', 'Sucursal Central', 'America/Mexico_City', 'MXN')
ON CONFLICT (branch_id) DO NOTHING;

INSERT INTO organization.warehouses (warehouse_id, branch_id, code, name, warehouse_type)
VALUES ('00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'VENTA', 'Almacén de venta', 'STORE')
ON CONFLICT (warehouse_id) DO NOTHING;

INSERT INTO organization.devices (device_id, branch_id, warehouse_id, device_code, device_type, platform)
VALUES ('00000000-0000-0000-0000-000000000012', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000011', 'POS-DEV-01', 'POS', 'DOCKER-TEST')
ON CONFLICT (device_id) DO NOTHING;

INSERT INTO catalog.units_of_measure (unit_id, code, name, symbol, quantity_scale)
VALUES
 ('00000000-0000-0000-0000-000000000101', 'PZA', 'Pieza', 'pza', 0),
 ('00000000-0000-0000-0000-000000000102', 'KG', 'Kilogramo', 'kg', 3)
ON CONFLICT (unit_id) DO NOTHING;

INSERT INTO catalog.categories (category_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000111', 'ABARROTES', 'Abarrotes')
ON CONFLICT (category_id) DO NOTHING;

INSERT INTO catalog.brands (brand_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000121', 'GENERICO', 'Genérico')
ON CONFLICT (brand_id) DO NOTHING;

INSERT INTO catalog.taxes (tax_id, code, name, rate, sat_tax_code)
VALUES ('00000000-0000-0000-0000-000000000131', 'IVA16', 'IVA 16%', 0.16, '002')
ON CONFLICT (tax_id) DO NOTHING;

INSERT INTO catalog.products (product_id, category_id, brand_id, name, tracks_inventory, tracks_lots, tracks_expiration)
VALUES ('00000000-0000-0000-0000-000000000141', '00000000-0000-0000-0000-000000000111', '00000000-0000-0000-0000-000000000121', 'Producto de prueba', TRUE, TRUE, TRUE)
ON CONFLICT (product_id) DO NOTHING;

INSERT INTO catalog.product_presentations (product_presentation_id, product_id, unit_id, tax_id, sku, name, minimum_stock)
VALUES ('00000000-0000-0000-0000-000000000151', '00000000-0000-0000-0000-000000000141', '00000000-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000131', 'TEST-PZA-001', 'Presentación de prueba', 1)
ON CONFLICT (product_presentation_id) DO NOTHING;

INSERT INTO catalog.barcodes (barcode_id, product_presentation_id, barcode, barcode_type, is_primary)
VALUES ('00000000-0000-0000-0000-000000000161', '00000000-0000-0000-0000-000000000151', '7500000000001', 'EAN13', TRUE)
ON CONFLICT (barcode_id) DO NOTHING;

INSERT INTO catalog.price_lists (price_list_id, code, name)
VALUES ('00000000-0000-0000-0000-000000000171', 'GENERAL', 'Precio general')
ON CONFLICT (price_list_id) DO NOTHING;

INSERT INTO catalog.prices (price_id, price_list_id, branch_id, product_presentation_id, amount)
VALUES ('00000000-0000-0000-0000-000000000181', '00000000-0000-0000-0000-000000000171', '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000151', 25.00)
ON CONFLICT (price_id) DO NOTHING;

INSERT INTO inventory.stock_balances (stock_balance_id, warehouse_id, product_presentation_id, on_hand_quantity, average_unit_cost)
VALUES ('00000000-0000-0000-0000-000000000191', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000151', 10, 15.00)
ON CONFLICT (stock_balance_id) DO NOTHING;

INSERT INTO iam.roles (role_id, code, name, is_system)
VALUES
 ('00000000-0000-0000-0000-000000000201', 'ADMIN', 'Administrador', TRUE),
 ('00000000-0000-0000-0000-000000000202', 'CASHIER', 'Cajero', TRUE),
 ('00000000-0000-0000-0000-000000000203', 'INVENTORY_MANAGER', 'Encargado de inventario', TRUE)
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO iam.permissions (permission_id, code, name, module)
VALUES
 ('00000000-0000-0000-0000-000000000211', 'PRODUCT_READ', 'Consultar productos', 'CATALOG'),
 ('00000000-0000-0000-0000-000000000212', 'SALE_CREATE', 'Registrar ventas', 'SALES'),
 ('00000000-0000-0000-0000-000000000213', 'INVENTORY_ADJUST', 'Ajustar inventario', 'INVENTORY'),
 ('00000000-0000-0000-0000-000000000214', 'AUDIT_READ', 'Consultar auditoría', 'AUDIT')
ON CONFLICT (permission_id) DO NOTHING;

COMMIT;

